// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.withLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.caches.project.getDependentModules
import org.jetbrains.kotlin.idea.caches.project.getIdeaModelInfosCache
import org.jetbrains.kotlin.idea.caches.project.getModuleInfosFromIdeaModel
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.stubs.createMultiplatformFacetM3
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase.addJdk
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.projectModel.FullJdk
import org.jetbrains.kotlin.projectModel.KotlinSdk
import org.jetbrains.kotlin.projectModel.ResolveSdk
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.util.addDependency
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.moduleLibrary
import org.jetbrains.kotlin.test.util.projectLibrary
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains

@RunWith(JUnit38ClassRunner::class)
class IdeaModuleInfoTest8 : JavaModuleTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null

    private fun getModuleInfoFromIdeaModuleWithJvmPlatformCheck(): List<IdeaModuleInfo> {
        val common = getModuleInfosFromIdeaModel(project)
        val jvm = getModuleInfosFromIdeaModel(project, JvmPlatforms.defaultJvmPlatform)
        val commonAsSet = common.toHashSet()
        assertEquals(commonAsSet.size, jvm.size)
        for (fromJvm in jvm) {
            assertContains(commonAsSet, fromJvm)
        }

        common.forEach(IdeaModuleInfo::checkValidity)
        return common
    }

    fun testMutateLibraryRoots() {
        val library = projectLibrary(
            libraryName = "myLib",
            classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
        )

        val a = module("a")
        a.addDependency(library)
        getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        runWriteAction {
            val modifiableModel = library.modifiableModel
            modifiableModel.removeRoot(TestKotlinArtifacts.kotlinDaemon.jarRoot.url, OrderRootType.SOURCES)
            modifiableModel.commit()
        }

        assertEmpty(library.cast<LibraryEx>().getUrls(OrderRootType.SOURCES))

        getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        runWriteAction {
            val modifiableModel = library.modifiableModel
            modifiableModel.addRoot(TestKotlinArtifacts.kotlinDaemon.jarRoot.url, OrderRootType.SOURCES)
            modifiableModel.commit()
        }

        assertNotEmpty(library.cast<LibraryEx>().getUrls(OrderRootType.SOURCES).toList())

        getModuleInfoFromIdeaModuleWithJvmPlatformCheck()
    }

    fun testAllModulesCache() {
        val stdlib = stdlibJvm()
        module.addDependency(stdlib)

        val daemon = projectLibrary("daemon", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        assertEquals(2 /* sdk + stdlib */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        module.addDependency(daemon)
        assertEquals(3 /* sdk + stdlib + daemon */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        val fakeLib = projectLibraryWithFakeRoot("f")
        fakeLib.toLibraryInfo()

        assertEquals(3 /* sdk + stdlib + daemon */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        module.addDependency(fakeLib)
        assertEquals(4 /* sdk + stdlib + daemon + fakeLib */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        module("a", hasProductionRoot = true, hasTestRoot = true)
        assertEquals(6 /* sdk + stdlib + daemon + fakeLib + 2 roots of a */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        ModuleRootModificationUtil.removeDependency(module, fakeLib)
        assertEquals(5 /* sdk + stdlib + daemon + 2 roots of a */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)

        runWriteAction {
            LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeLibrary(daemon)
        }

        assertEquals(4 /* sdk + stdlib + 2 roots of a */, getModuleInfoFromIdeaModuleWithJvmPlatformCheck().size)
    }

    fun testLowMemory() {
        val moduleA = module("a")
        val stdlib = stdlibJvm()
        moduleA.addDependency(stdlib)

        val modelBefore = getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        val stdlibInfoBefore = stdlib.toLibraryInfo()

        assertContains(modelBefore, stdlibInfoBefore)

        LowMemoryWatcher.onLowMemorySignalReceived(true)

        val modelAfter = getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        val stdlibInfoAfter = stdlib.toLibraryInfo()

        assertContains(modelAfter, stdlibInfoAfter)
    }

    fun testLowMemoryLibraryDependenciesCache() {
        val moduleA = module("a")
        val daemon = projectLibrary("kotlin-stdlib", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val myLib = projectLibraryWithFakeRoot("myLib")
        moduleA.addDependency(daemon)
        moduleA.addDependency(myLib)

        val moduleB = module("b")
        val daemonCopy = projectLibrary("kotlin-stdlib-copy", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        moduleB.addDependency(daemonCopy)

        val daemonInfo = daemon.toLibraryInfo()
        val daemonCopyInfo = daemonCopy.toLibraryInfo()
        assertEquals(daemon, daemonInfo.library)
        assertNotEquals(daemonCopy, daemonCopyInfo.library)
        assertEquals(daemonInfo, daemonCopyInfo)

        val dependenciesCache = LibraryDependenciesCache.getInstance(project)
        val dependenciesBefore = dependenciesCache.getLibraryDependencies(daemonInfo)
        assertEquals(dependenciesBefore.libraries, listOf(daemonInfo, myLib.toLibraryInfo()))

        LowMemoryWatcher.onLowMemorySignalReceived(true)

        val daemonCopyInfoAfter = daemonCopy.toLibraryInfo()
        val dependenciesAfter = dependenciesCache.getLibraryDependencies(daemonCopyInfoAfter)
        assertEquals(dependenciesAfter.libraries, listOf(daemonCopyInfoAfter, myLib.toLibraryInfo()))
    }

    fun testStdlibDependencies() {
        val moduleA = module("a")
        val stdlib = projectLibrary("kotlin-stdlib", TestKotlinArtifacts.kotlinStdlib.jarRoot)
        val myLib = projectLibraryWithFakeRoot("myLib")
        moduleA.addDependency(stdlib)
        moduleA.addDependency(myLib)

        val moduleB = module("b")
        val stdlibCopy = projectLibrary("kotlin-stdlib-copy", TestKotlinArtifacts.kotlinStdlib.jarRoot)
        moduleB.addDependency(stdlibCopy)

        val stdlibInfo = stdlib.toLibraryInfo()
        val stdlibCopyInfo = stdlibCopy.toLibraryInfo()
        assertEquals(stdlib, stdlibInfo.library)
        assertNotEquals(stdlibCopy, stdlibCopyInfo.library)
        assertEquals(stdlibInfo, stdlibCopyInfo)

        val myLibInfo = myLib.toLibraryInfo()
        val dependenciesCache = LibraryDependenciesCache.getInstance(project)
        val dependenciesBefore = dependenciesCache.getLibraryDependencies(stdlibInfo)
        // to check org.jetbrains.kotlin.idea.base.analysis.LibraryDependenciesCacheImpl#filterForBuiltins
        assertEquals(dependenciesBefore.libraries, listOf(stdlibInfo))
        assertDoesntContain(dependenciesBefore.libraries, myLibInfo)
    }

    fun testLibraryCacheRace() {
        val moduleA = module("a")
        val stdlib = projectLibrary("kotlin-stdlib", TestKotlinArtifacts.kotlinStdlib.jarRoot)
        moduleA.addDependency(stdlib)

        val moduleB = module("b")
        val stdlibCopy = projectLibrary("kotlin-stdlib-copy", TestKotlinArtifacts.kotlinStdlib.jarRoot)
        moduleB.addDependency(stdlibCopy)

        val modelBefore = getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        val stdlibCopyInfoBefore = stdlibCopy.toLibraryInfo()

        assertContains(modelBefore, stdlibCopyInfoBefore)

        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        runWriteAction {
            libraryTable.removeLibrary(stdlib)
        }

        val modelAfter = getModuleInfoFromIdeaModuleWithJvmPlatformCheck()

        val stdlibCopyInfoAfter = stdlibCopy.toLibraryInfo()
        assertNotEquals(stdlibCopyInfoBefore, stdlibCopyInfoAfter)

        assertDoesntContain(modelAfter, stdlibCopyInfoBefore)
        assertContains(modelAfter, stdlibCopyInfoAfter)
    }

    fun testCacheRace() {
        val cache = LibraryInfoCache.getInstance(project)
        val resultSet: MutableSet<List<LibraryInfo>> = ConcurrentCollectionFactory.createConcurrentIdentitySet<List<LibraryInfo>>()
        val libraries = List(50) { index -> projectLibrary("kotlin-stdlib-$index", TestKotlinArtifacts.kotlinStdlib.jarRoot) }
        val features = libraries.map {
            executeOnPooledThread {
                val value: List<LibraryInfo> = runReadAction { cache[it] }
                resultSet.add(value)
            }
        }

        features.forEach { it.get(60, TimeUnit.SECONDS) }
        assertEquals(/* expected = */ 1, /* actual = */ resultSet.size)
    }

    fun testDependenciesFromDuplicatedLibraries() {
        val (a, b, c) = modules()

        val daemonLibrary1 = projectLibrary("daemon-1", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        a.addDependency(daemonLibrary1)

        val stdlib = stdlibJvm()
        val daemonLibrary2 = projectLibrary("daemon-2", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        b.addDependency(daemonLibrary2)
        b.addDependency(stdlib)

        val fakeLib = projectLibraryWithFakeRoot("fake")
        c.addDependency(fakeLib)

        val daemonLibraryInfo = daemonLibrary1.toLibraryInfo()
        val stdlibInfo = stdlib.toLibraryInfo()

        val dependenciesCache = LibraryDependenciesCache.getInstance(project)
        val dependencies = dependenciesCache.getLibraryDependencies(daemonLibraryInfo)
        assertEquals(listOf(daemonLibraryInfo, stdlibInfo), dependencies.libraries)
    }

    fun testCacheDeduplication() {
        val stdlib = stdlibJvm()
        val stdlibCopy = projectLibrary("kotlin-stdlib-copy", TestKotlinArtifacts.kotlinStdlib.jarRoot)
        val myLib = projectLibraryWithFakeRoot("myLib")

        assertNotEquals(stdlib, stdlibCopy)
        assertNotEquals(stdlib, myLib)
        assertNotEquals(stdlibCopy, myLib)

        val stdlibInfo = stdlib.toLibraryInfo().also(LibraryInfo::checkValidity)
        val stdlibCopyInfo = stdlibCopy.toLibraryInfo().also(LibraryInfo::checkValidity)
        val myLibInfo = myLib.toLibraryInfo().also(LibraryInfo::checkValidity)

        assertEquals(stdlibInfo, stdlibCopyInfo)
        assertNotEquals(stdlibInfo, myLibInfo)
        assertNotEquals(stdlibCopyInfo, myLibInfo)

        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        runWriteAction { libraryTable.removeLibrary(stdlib) }

        assertTrue(stdlib.isDisposed)
        assertTrue(stdlibInfo.isDisposed)

        assertFalse(stdlibCopy.isDisposed)
        assertTrue(stdlibCopyInfo.isDisposed)

        assertFalse(myLib.isDisposed)
        assertFalse(myLibInfo.isDisposed)

        val newStdlibCopyInfo = stdlibCopy.toLibraryInfo().also(LibraryInfo::checkValidity)
        assertNotEquals(stdlibCopyInfo, newStdlibCopyInfo)

        val newStdlib = stdlibJvm()
        val newStdlibInfo = newStdlib.toLibraryInfo().also(LibraryInfo::checkValidity)
        assertEquals(newStdlibInfo, newStdlibCopyInfo)

        runWriteAction { libraryTable.removeLibrary(newStdlib) }
        assertTrue(newStdlib.isDisposed)
        assertFalse(newStdlibInfo.isDisposed)

        val updatedStdlibCopyInfo = stdlibCopy.toLibraryInfo().also(LibraryInfo::checkValidity)
        assertEquals(newStdlibCopyInfo, updatedStdlibCopyInfo)
    }

    fun testSimpleModuleDependency() {
        val (a, b) = modules()
        b.addDependency(a)

        b.production.assertDependenciesEqual(b.production, a.production)
        UsefulTestCase.assertDoesntContain(a.production.dependencies(), b.production)
    }

    fun testCircularDependency() {
        val (a, b) = modules()

        b.addDependency(a)
        a.addDependency(b)

        a.production.assertDependenciesEqual(a.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production)
    }

    fun testModuleCircularDependencyLibraries() {
        val s0 = module("s0")
        val (a, b, c) = modules()


        s0.addDependency(a)
        a.addDependency(b)
        b.addDependency(c)
        c.addDependency(a)

        val kotlinStdlib = stdlibJvm()
        val fakeLib = projectLibraryWithFakeRoot("fake")
        val myLib = projectLibrary(
            libraryName = "myLib",
            classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
        )
        s0.addDependency(myLib)

        val myLibInfo = myLib.toLibraryInfo()
        val kotlinStdlibLibraryInfo = kotlinStdlib.toLibraryInfo()
        val fakeLibraryInfo = fakeLib.toLibraryInfo()

        c.addDependency(kotlinStdlib)
        b.addDependency(fakeLib)

        s0.production.assertDependenciesEqual(s0.production, a.production, myLibInfo)
        a.production.assertDependenciesEqual(a.production, b.production)
        b.production.assertDependenciesEqual(b.production, c.production, fakeLibraryInfo)
        c.production.assertDependenciesEqual(c.production, a.production, kotlinStdlibLibraryInfo)

        val dependenciesCache = LibraryDependenciesCache.getInstance(project)

        val dependencies = dependenciesCache.getLibraryDependencies(myLibInfo)
        assertEquals(dependencies.libraries.sortedBy { it.name },
                     listOf(fakeLibraryInfo, kotlinStdlibLibraryInfo, myLibInfo))
    }

    fun testModuleCircularDependencyLibraries2() {
        /**
         ```mermaid
         graph TD
         S0 --> A
         A --> D
         A --> B
         B --> C
         C --> A
         ```
         */
        val s0 = module("s0")
        val (a, b, c) = modules()
        val d = module("d")

        val s0Lib = projectLibrary("s0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val s0LibInfo = s0Lib.toLibraryInfo()

        s0.addDependency(a)
        s0.addDependency(s0Lib)

        val aLib = projectLibrary("aLib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val aLibInfo = aLib.toLibraryInfo()

        a.addDependency(b)
        a.addDependency(d)
        a.addDependency(aLib)

        val bLib = projectLibrary("bLib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val bLibInfo = bLib.toLibraryInfo()

        b.addDependency(c)
        b.addDependency(bLib)

        val cLib = projectLibrary("cLib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val cLibInfo = cLib.toLibraryInfo()

        c.addDependency(a)
        c.addDependency(cLib)

        val dLib = projectLibrary("dLib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val dLibInfo = dLib.toLibraryInfo()

        d.addDependency(dLib)

        s0.production.assertDependenciesEqual(
            s0.production,
            a.production,
            s0LibInfo,
        )

        a.production.assertDependenciesEqual(
            a.production,
            b.production,
            d.production,
            aLibInfo,
        )

        b.production.assertDependenciesEqual(
            b.production,
            c.production,
            bLibInfo,
        )

        c.production.assertDependenciesEqual(
            c.production,
            a.production,
            cLibInfo,
        )

        d.production.assertDependenciesEqual(
            d.production,
            dLibInfo,
        )

        assertDependencies(
            lib = s0LibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
            s0LibInfo,
        )

        assertDependencies(
            lib = aLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = bLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = cLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = dLibInfo,
            dLibInfo,
        )
    }

    fun testModuleCircularDependencyLibraries3() {
        /**
         ```mermaid
         graph
         S0 --> A
         A --> D
         A --> B
         B --> C
         C --> A
         ```
         */
        val s0 = module("s0")
        val (a, b, c) = modules()
        val d = module("d")

        val s0Lib = projectLibrary("s0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val s0LibInfo = s0Lib.toLibraryInfo()

        s0.addDependency(a)
        s0.addDependency(s0Lib)

        val aLib = projectLibrary("aLib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val aLibInfo = aLib.toLibraryInfo()

        a.addDependency(d)
        a.addDependency(b)
        a.addDependency(aLib)

        val bLib = projectLibrary("bLib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val bLibInfo = bLib.toLibraryInfo()

        b.addDependency(c)
        b.addDependency(bLib)

        val cLib = projectLibrary("cLib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val cLibInfo = cLib.toLibraryInfo()

        c.addDependency(a)
        c.addDependency(cLib)

        val dLib = projectLibrary("dLib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val dLibInfo = dLib.toLibraryInfo()

        d.addDependency(dLib)

        s0.production.assertDependenciesEqual(
            s0.production,
            a.production,
            s0LibInfo,
        )

        a.production.assertDependenciesEqual(
            a.production,
            d.production,
            b.production,
            aLibInfo,
        )

        b.production.assertDependenciesEqual(
            b.production,
            c.production,
            bLibInfo,
        )

        c.production.assertDependenciesEqual(
            c.production,
            a.production,
            cLibInfo,
        )

        d.production.assertDependenciesEqual(
            d.production,
            dLibInfo,
        )

        assertDependencies(
            lib = s0LibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
            s0LibInfo,
        )

        assertDependencies(
            lib = aLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = bLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = cLibInfo,
            aLibInfo,
            bLibInfo,
            cLibInfo,
            dLibInfo,
        )

        assertDependencies(
            lib = dLibInfo,
            dLibInfo,
        )
    }

    fun testModuleCircularDependencyLibraries4() {
        /**
        ```mermaid
        graph
        M0 --> M1
        M1 --> M2
        M2 --> M3
        M3 --> M5
        M5 --> M6
        M6 --> M1
        M3 --> M4
        M4 --> M2
        ```
         */
        val m0 = module("m0")
        val m1 = module("m1")
        val m2 = module("m2")
        val m3 = module("m3")
        val m4 = module("m4")
        val m5 = module("m5")
        val m6 = module("m6")

        m0.addDependency(m1)
        m1.addDependency(m2)
        m2.addDependency(m3)
        m3.addDependency(m5)
        m5.addDependency(m6)
        m6.addDependency(m1)
        m3.addDependency(m4)
        m4.addDependency(m2)

        val m0Lib = projectLibrary("m0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val m0LibInfo = m0Lib.toLibraryInfo()

        m0.addDependency(m0Lib)

        val m1Lib = projectLibrary("m1Lib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val m1LibInfo = m1Lib.toLibraryInfo()

        m1.addDependency(m1Lib)

        val m2Lib = projectLibrary("m2Lib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val m2LibInfo = m2Lib.toLibraryInfo()

        m2.addDependency(m2Lib)

        val m3Lib = projectLibrary("m3Lib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val m3LibInfo = m3Lib.toLibraryInfo()

        m3.addDependency(m3Lib)

        val m4Lib = projectLibrary("m4Lib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val m4LibInfo = m4Lib.toLibraryInfo()

        m4.addDependency(m4Lib)

        val m5Lib = projectLibrary("m5Lib", classesRoot = TestKotlinArtifacts.jsr305.jarRoot)
        val m5LibInfo = m5Lib.toLibraryInfo()

        m5.addDependency(m5Lib)

        val m6Lib = projectLibrary("m6Lib", classesRoot = TestKotlinArtifacts.kotlinTest.jarRoot)
        val m6LibInfo = m6Lib.toLibraryInfo()

        m6.addDependency(m6Lib)

        m0.production.assertDependenciesEqual(
            m0.production,
            m1.production,
            m0LibInfo,
        )

        m1.production.assertDependenciesEqual(
            m1.production,
            m2.production,
            m1LibInfo,
        )

        m2.production.assertDependenciesEqual(
            m2.production,
            m3.production,
            m2LibInfo,
        )

        m3.production.assertDependenciesEqual(
            m3.production,
            m5.production,
            m4.production,
            m3LibInfo,
        )

        m4.production.assertDependenciesEqual(
            m4.production,
            m2.production,
            m4LibInfo,
        )

        m5.production.assertDependenciesEqual(
            m5.production,
            m6.production,
            m5LibInfo,
        )

        m6.production.assertDependenciesEqual(
            m6.production,
            m1.production,
            m6LibInfo,
        )

        assertDependencies(
            lib = m0LibInfo,
            m0LibInfo,
            m1LibInfo,
            m2LibInfo,
            m3LibInfo,
            m4LibInfo,
            m5LibInfo,
            m6LibInfo,
        )

        for (libraryInfo in listOf(m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo)) {
            assertDependencies(
                lib = libraryInfo,
                m1LibInfo,
                m2LibInfo,
                m3LibInfo,
                m4LibInfo,
                m5LibInfo,
                m6LibInfo,
            )
        }
    }

    fun testModuleCircularDependencyLibraries5() {
        /**
        ```mermaid
        graph
        M01 --> M0
        M0 --> M1
        M1 --> M2
        M2 --> M3
        M3 --> M4
        M3 --> M7
        M4 --> M5
        M5 --> M3
        M4 --> M6
        M6 --> M10
        M7 --> M8
        M8 --> M1
        ```
         */
        val m01 = module("m01")
        val m0 = module("m0")
        val m1 = module("m1")
        val m2 = module("m2")
        val m3 = module("m3")
        val m4 = module("m4")
        val m5 = module("m5")
        val m6 = module("m6")
        val m7 = module("m7")
        val m8 = module("m8")
        val m10 = module("m10")

        m01.addDependency(m0)
        m0.addDependency(m1)
        m1.addDependency(m2)
        m2.addDependency(m3)
        m3.addDependency(m4)
        m3.addDependency(m7)
        m4.addDependency(m5)
        m4.addDependency(m6)
        m5.addDependency(m3)
        m6.addDependency(m10)
        m7.addDependency(m8)
        m8.addDependency(m1)

        val m01Lib = projectLibraryWithFakeRoot("fake")
        val m01LibInfo = m01Lib.toLibraryInfo()

        m01.addDependency(m01Lib)

        val m0Lib = projectLibrary("m0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val m0LibInfo = m0Lib.toLibraryInfo()

        m0.addDependency(m0Lib)

        val m1Lib = projectLibrary("m1Lib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val m1LibInfo = m1Lib.toLibraryInfo()

        m1.addDependency(m1Lib)

        val m2Lib = projectLibrary("m2Lib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val m2LibInfo = m2Lib.toLibraryInfo()

        m2.addDependency(m2Lib)

        val m3Lib = projectLibrary("m3Lib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val m3LibInfo = m3Lib.toLibraryInfo()

        m3.addDependency(m3Lib)

        val m4Lib = projectLibrary("m4Lib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val m4LibInfo = m4Lib.toLibraryInfo()

        m4.addDependency(m4Lib)

        val m5Lib = projectLibrary("m5Lib", classesRoot = TestKotlinArtifacts.jsr305.jarRoot)
        val m5LibInfo = m5Lib.toLibraryInfo()

        m5.addDependency(m5Lib)

        val m6Lib = projectLibrary("m6Lib", classesRoot = TestKotlinArtifacts.kotlinTest.jarRoot)
        val m6LibInfo = m6Lib.toLibraryInfo()

        m6.addDependency(m6Lib)

        val m7Lib = projectLibrary("m7Lib", classesRoot = TestKotlinArtifacts.kotlinScriptRuntime.jarRoot)
        val m7LibInfo = m7Lib.toLibraryInfo()

        m7.addDependency(m7Lib)

        val m8Lib = projectLibrary("m8Lib", classesRoot = TestKotlinArtifacts.junit3.jarRoot)
        val m8LibInfo = m8Lib.toLibraryInfo()

        m8.addDependency(m8Lib)

        val m10Lib = projectLibrary("m10Lib", classesRoot = TestKotlinArtifacts.jetbrainsAnnotations.jarRoot)
        val m10LibInfo = m10Lib.toLibraryInfo()

        m10.addDependency(m10Lib)

        m01.production.assertDependenciesEqual(
            m01.production,
            m0.production,
            m01LibInfo,
        )

        m0.production.assertDependenciesEqual(
            m0.production,
            m1.production,
            m0LibInfo,
        )

        m1.production.assertDependenciesEqual(
            m1.production,
            m2.production,
            m1LibInfo,
        )

        m2.production.assertDependenciesEqual(
            m2.production,
            m3.production,
            m2LibInfo,
        )

        m3.production.assertDependenciesEqual(
            m3.production,
            m4.production,
            m7.production,
            m3LibInfo,
        )

        m4.production.assertDependenciesEqual(
            m4.production,
            m5.production,
            m6.production,
            m4LibInfo,
        )

        m5.production.assertDependenciesEqual(
            m5.production,
            m3.production,
            m5LibInfo,
        )

        m6.production.assertDependenciesEqual(
            m6.production,
            m10.production,
            m6LibInfo,
        )

        m10.production.assertDependenciesEqual(
            m10.production,
            m10LibInfo,
        )

        m7.production.assertDependenciesEqual(
            m7.production,
            m8.production,
            m7LibInfo,
        )

        m8.production.assertDependenciesEqual(
            m8.production,
            m1.production,
            m8LibInfo,
        )

        assertDependencies(
            lib = m01LibInfo,
            m01LibInfo, m0LibInfo, m10LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo, m8LibInfo
        )

        assertDependencies(
            lib = m0LibInfo,
            m0LibInfo, m10LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo, m8LibInfo
        )

        for (libraryInfo in listOf(m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m7LibInfo, m8LibInfo)) {
            assertDependencies(
                lib = libraryInfo,
                m10LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo, m8LibInfo
            )
        }

        assertDependencies(lib = m10LibInfo, m10LibInfo)
        assertDependencies(lib = m6LibInfo, m10LibInfo, m6LibInfo)
    }

    fun testModuleCircularDependencyLibraries6() {
        /**
        ```mermaid
        graph
        M0 --> M1
        M1 --> M2
        M2 --> M3
        M3 --> M4
        M4 --> M5
        M5 --> M3
        M5 --> M6
        M6 --> M7
        M7 --> M1
        ```
         */
        val m0 = module("m0")
        val m1 = module("m1")
        val m2 = module("m2")
        val m3 = module("m3")
        val m4 = module("m4")
        val m5 = module("m5")
        val m6 = module("m6")
        val m7 = module("m7")

        m0.addDependency(m1)
        m1.addDependency(m2)
        m2.addDependency(m3)
        m3.addDependency(m4)
        m4.addDependency(m5)
        m5.addDependency(m3)
        m5.addDependency(m6)
        m6.addDependency(m7)
        m7.addDependency(m1)

        val m0Lib = projectLibrary("m0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val m0LibInfo = m0Lib.toLibraryInfo()

        m0.addDependency(m0Lib)

        val m1Lib = projectLibrary("m1Lib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val m1LibInfo = m1Lib.toLibraryInfo()

        m1.addDependency(m1Lib)

        val m2Lib = projectLibrary("m2Lib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val m2LibInfo = m2Lib.toLibraryInfo()

        m2.addDependency(m2Lib)

        val m3Lib = projectLibrary("m3Lib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val m3LibInfo = m3Lib.toLibraryInfo()

        m3.addDependency(m3Lib)

        val m4Lib = projectLibrary("m4Lib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val m4LibInfo = m4Lib.toLibraryInfo()

        m4.addDependency(m4Lib)

        val m5Lib = projectLibrary("m5Lib", classesRoot = TestKotlinArtifacts.jsr305.jarRoot)
        val m5LibInfo = m5Lib.toLibraryInfo()

        m5.addDependency(m5Lib)

        val m6Lib = projectLibrary("m6Lib", classesRoot = TestKotlinArtifacts.kotlinTest.jarRoot)
        val m6LibInfo = m6Lib.toLibraryInfo()

        m6.addDependency(m6Lib)

        val m7Lib = projectLibrary("m7Lib", classesRoot = TestKotlinArtifacts.kotlinScriptRuntime.jarRoot)
        val m7LibInfo = m7Lib.toLibraryInfo()

        m7.addDependency(m7Lib)

        m0.production.assertDependenciesEqual(
            m0.production,
            m1.production,
            m0LibInfo,
        )

        m1.production.assertDependenciesEqual(
            m1.production,
            m2.production,
            m1LibInfo,
        )

        m2.production.assertDependenciesEqual(
            m2.production,
            m3.production,
            m2LibInfo,
        )

        m3.production.assertDependenciesEqual(
            m3.production,
            m4.production,
            m3LibInfo,
        )

        m4.production.assertDependenciesEqual(
            m4.production,
            m5.production,
            m4LibInfo,
        )

        m5.production.assertDependenciesEqual(
            m5.production,
            m3.production,
            m6.production,
            m5LibInfo,
        )

        m6.production.assertDependenciesEqual(
            m6.production,
            m7.production,
            m6LibInfo,
        )

        m7.production.assertDependenciesEqual(
            m7.production,
            m1.production,
            m7LibInfo,
        )

        assertDependencies(
            lib = m0LibInfo,
            m0LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo
        )

        for (libraryInfo in listOf(m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m7LibInfo)) {
            assertDependencies(
                lib = libraryInfo,
                m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo
            )
        }

        // drop module in the middle of the loop
        ModuleRootModificationUtil.updateModel(m4) { model: ModifiableRootModel ->
            val entry = model.findModuleOrderEntry(m5) ?: error("unable to find m5")
            model.removeOrderEntry(entry)
        }

        m4.production.assertDependenciesEqual(
            m4.production,
            // m5 is dropped
            // m5.production,
            m4LibInfo,
        )

        assertDependencies(lib = m1LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo)
        assertDependencies(lib = m2LibInfo, m2LibInfo, m3LibInfo, m4LibInfo)
        assertDependencies(lib = m3LibInfo, m3LibInfo, m4LibInfo)
        assertDependencies(lib = m4LibInfo, m4LibInfo)

        assertDependencies(lib = m6LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m6LibInfo, m7LibInfo)
        assertDependencies(lib = m7LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m7LibInfo)
    }

    fun testMppCase() {
        /**
        ```mermaid
        graph
        common --> STDLIB_COMMON
        js --> common
        js --> STDLIB_JS
        js --> KOTLIN_SDK
        wasm --> common
        wasm --> STDLIB_WASM
        wasm --> KOTLIN_SDK
        jvmNoSdk --> common
        jvmNoSdk --> STDLIB_JVM
        jvmWithSdk --> common
        jvmWithSdk --> STDLIB_JVM
        jvmWithSdk --> FULL_JDK
        native --> common
        common --> KOTLIN_SDK
        ```
         */

        KotlinSdkType.setUpIfNeeded(testRootDisposable)

        val common = module("common")
        val js = module("js")
        val wasm = module("wasm")
        val jvmNoSdk = module("jvmNoSdk")
        val jvmWithSdk = module("jvmWithSdk")
        val native = module("native")

        js.addDependency(common)
        wasm.addDependency(common)
        jvmNoSdk.addDependency(common)
        jvmWithSdk.addDependency(common)
        native.addDependency(common)

        val stdlibCommon = projectLibrary("stdlibCommon", classesRoot = TestKotlinArtifacts.kotlinStdlibCommon.jarRoot)
        val stdlibCommonLibInfo = stdlibCommon.toLibraryInfo()

        common.addDependency(stdlibCommon)
        common.addDependency(KotlinSdk, testRootDisposable)
        jvmWithSdk.addDependency(KotlinSdk, testRootDisposable)

        val kotlinSdkInfo = common.toSdkInfo(KotlinSdk) ?: error("no KotlinSDK")

        val stdlibJs = projectLibrary("stdlibJs", classesRoot = TestKotlinArtifacts.kotlinStdlibJs.jarRoot)
        val stdlibJsInfo = stdlibJs.toLibraryInfo()

        js.addDependency(stdlibJs)
        js.addDependency(KotlinSdk, testRootDisposable)

        val stdlibWasm = projectLibrary("stdlibWasm", classesRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(TestKotlinArtifacts.kotlinStdlibWasm))
        val stdlibWasmInfo = stdlibWasm.toLibraryInfo()

        wasm.addDependency(stdlibWasm)
        wasm.addDependency(KotlinSdk, testRootDisposable)

        val stdlibJvm = projectLibrary("stdlibJvm", classesRoot = TestKotlinArtifacts.kotlinStdlib.jarRoot)
        val stdlibJvmLibInfo = stdlibJvm.toLibraryInfo()

        jvmNoSdk.addDependency(stdlibJvm)

        val fakeLib = projectLibraryWithFakeRoot("fakeLib")
        jvmNoSdk.addDependency(fakeLib)
        val fakeLibLibraryInfo = fakeLib.toLibraryInfo()

        jvmWithSdk.addDependency(stdlibJvm)

        jvmWithSdk.addDependency(FullJdk, testRootDisposable)
        val fullSdkInfo = jvmWithSdk.toSdkInfo(FullJdk) ?: error("no FullJdk")

        common.production.assertDependenciesEqual(
            kotlinSdkInfo,
            common.production,
            stdlibCommonLibInfo
        )

        js.production.assertDependenciesEqual(
            kotlinSdkInfo,
            js.production,
            common.production,
            stdlibJsInfo
        )

        wasm.production.assertDependenciesEqual(
            kotlinSdkInfo,
            wasm.production,
            common.production,
            stdlibWasmInfo
        )

        jvmNoSdk.production.assertDependenciesEqual(
            jvmNoSdk.production,
            common.production,
            stdlibJvmLibInfo,
            fakeLibLibraryInfo
        )

        jvmWithSdk.production.assertDependenciesEqual(
            fullSdkInfo,
            jvmWithSdk.production,
            common.production,
            stdlibJvmLibInfo,
        )

        assertDependencies(
            lib = stdlibCommonLibInfo,
            expectedSdkInfos = listOf(kotlinSdkInfo),
            stdlibCommonLibInfo
        )

        assertDependencies(
            lib = stdlibJsInfo,
            expectedSdkInfos = listOf(kotlinSdkInfo),
            stdlibCommonLibInfo, stdlibJsInfo
        )

        assertDependencies(
            lib = stdlibWasmInfo,
            expectedSdkInfos = listOf(kotlinSdkInfo),
            stdlibCommonLibInfo, stdlibWasmInfo
        )

        assertDependencies(
            lib = fakeLibLibraryInfo,
            expectedSdkInfos = listOf(),
            fakeLibLibraryInfo, stdlibCommonLibInfo, stdlibJvmLibInfo
        )

        assertDependencies(
            lib = stdlibJvmLibInfo,
            expectedSdkInfos = listOf(fullSdkInfo),
            stdlibCommonLibInfo, stdlibJvmLibInfo
        )

        assertDependencies(
            lib = stdlibJvmLibInfo,
            expectedSdkInfos = listOf(fullSdkInfo),
            stdlibCommonLibInfo, stdlibJvmLibInfo
        )
    }


    fun testModuleChainInvalidation() {
        /**
        ```mermaid
        graph
        M0 --> M1
        M1 --> M2
        M2 --> M3
        M3 --> M4
        M1 --> M5
        M5 --> M6
        M5 --> M7
        ```
         */
        val m0 = module("m0")
        val m1 = module("m1")
        val m2 = module("m2")
        val m3 = module("m3")
        val m4 = module("m4")
        val m5 = module("m5")
        val m6 = module("m6")
        val m7 = module("m7")

        m0.addDependency(m1)
        m1.addDependency(m2)
        m2.addDependency(m3)
        m3.addDependency(m4)
        m1.addDependency(m5)
        m5.addDependency(m6)
        m5.addDependency(m7)

        val m0Lib = projectLibrary("m0Lib", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val m0LibInfo = m0Lib.toLibraryInfo()

        m0.addDependency(m0Lib)

        val m1Lib = projectLibrary("m1Lib", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        val m1LibInfo = m1Lib.toLibraryInfo()

        m1.addDependency(m1Lib)

        val m2Lib = projectLibrary("m2Lib", classesRoot = TestKotlinArtifacts.kotlinAnnotationsJvm.jarRoot)
        val m2LibInfo = m2Lib.toLibraryInfo()

        m2.addDependency(m2Lib)

        val m3Lib = projectLibrary("m3Lib", classesRoot = TestKotlinArtifacts.kotlinTestJunit.jarRoot)
        val m3LibInfo = m3Lib.toLibraryInfo()

        m3.addDependency(m3Lib)

        val m4Lib = projectLibrary("m4Lib", classesRoot = TestKotlinArtifacts.parcelizeRuntime.jarRoot)
        val m4LibInfo = m4Lib.toLibraryInfo()

        m4.addDependency(m4Lib)

        val m5Lib = projectLibrary("m5Lib", classesRoot = TestKotlinArtifacts.jsr305.jarRoot)
        val m5LibInfo = m5Lib.toLibraryInfo()

        m5.addDependency(m5Lib)

        val m6Lib = projectLibrary("m6Lib", classesRoot = TestKotlinArtifacts.kotlinTest.jarRoot)
        val m6LibInfo = m6Lib.toLibraryInfo()

        m6.addDependency(m6Lib)

        val m7Lib = projectLibrary("m7Lib", classesRoot = TestKotlinArtifacts.kotlinScriptRuntime.jarRoot)
        val m7LibInfo = m7Lib.toLibraryInfo()

        m7.addDependency(m7Lib)

        m0.production.assertDependenciesEqual(
            m0.production,
            m1.production,
            m0LibInfo,
        )

        m1.production.assertDependenciesEqual(
            m1.production,
            m2.production,
            m5.production,
            m1LibInfo,
        )

        m2.production.assertDependenciesEqual(
            m2.production,
            m3.production,
            m2LibInfo,
        )

        m3.production.assertDependenciesEqual(
            m3.production,
            m4.production,
            m3LibInfo,
        )

        m4.production.assertDependenciesEqual(
            m4.production,
            m4LibInfo,
        )

        m5.production.assertDependenciesEqual(
            m5.production,
            m6.production,
            m7.production,
            m5LibInfo,
        )

        m6.production.assertDependenciesEqual(
            m6.production,
            m6LibInfo,
        )

        m7.production.assertDependenciesEqual(
            m7.production,
            m7LibInfo,
        )

        assertDependencies(
            lib = m0LibInfo,
            m0LibInfo, m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo
        )

        assertDependencies(
            lib = m1LibInfo,
            m1LibInfo, m2LibInfo, m3LibInfo, m4LibInfo, m5LibInfo, m6LibInfo, m7LibInfo
        )

        assertDependencies(
            lib = m2LibInfo,
            m2LibInfo, m3LibInfo, m4LibInfo
        )

        assertDependencies(
            lib = m3LibInfo,
            m3LibInfo, m4LibInfo
        )

        assertDependencies(
            lib = m5LibInfo,
            m5LibInfo, m6LibInfo, m7LibInfo
        )

        // it has to drop m2, m1, m0
        ModuleRootModificationUtil.updateModel(m1) { model: ModifiableRootModel ->
            val entry = model.findModuleOrderEntry(m2) ?: error("unable to find m2")
            model.removeOrderEntry(entry)
        }

        assertDependencies(
            lib = m1LibInfo,
            m1LibInfo, m5LibInfo, m6LibInfo, m7LibInfo
        )

        assertDependencies(
            lib = m2LibInfo,
            m2LibInfo, m3LibInfo, m4LibInfo
        )

        assertDependencies(
            lib = m3LibInfo,
            m3LibInfo, m4LibInfo
        )

        assertDependencies(
            lib = m5LibInfo,
            m5LibInfo, m6LibInfo, m7LibInfo
        )
    }

    fun testDependencyResolutionAnchors() {
        val module1 = module("M1")
        val lib1 = projectLibrary("Lib1", classesRoot = TestKotlinArtifacts.kotlinCompiler.jarRoot)
        val lib2 = projectLibrary("Lib2", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        module1.addDependency(lib1)
        module1.addDependency(lib2)

        val module2 = module("M2")
        val lib3 = projectLibrary("Lib3", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        module2.addDependency(lib2)
        module2.addDependency(lib3)
        val anchorModule = module("anchor")
        val anchorModuleInfo = anchorModule.production

        val lib1Info = lib1.toLibraryInfo()
        val lib2Info = lib2.toLibraryInfo()
        val lib3Info = lib3.toLibraryInfo()

        module1.production.assertDependenciesEqual(
            module1.production,
            lib1Info,
            lib2Info,
        )
        module2.production.assertDependenciesEqual(
            module2.production,
            lib2Info,
            lib3Info,
        )

        assertDependencies(
            lib = lib1Info,
            lib1Info, lib2Info
        )
        assertDependencies(
            lib = lib2Info,
            lib1Info, lib2Info, lib3Info
        )

        val resolutionAnchorCacheService = ResolutionAnchorCacheService.getInstance(project) as ResolutionAnchorCacheServiceImpl
        val anchorMapping = mapOf(lib3.name!! to anchorModule.name)
        resolutionAnchorCacheService.setAnchors(anchorMapping)

        project.withLibraryToSourceAnalysis {
            for (lib in listOf(lib1Info, lib2Info, lib3Info)) {
                with(resolutionAnchorCacheService.getDependencyResolutionAnchors(lib)) {
                    assertEquals(lib.toString(), 1, this.size)
                    assertEquals(lib.toString(), anchorModuleInfo, this.first())
                }
            }
        }
    }

    fun testDependencyResolutionAnchorsWithStdlib() {
        val libraryWithAnchor = projectLibrary("LibraryWithAnchor", classesRoot = TestKotlinArtifacts.kotlinCompiler.jarRoot)
        val regularLibrary = projectLibrary("RegularLibrary", classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val stdlib = projectLibrary("stdlib", classesRoot = TestKotlinArtifacts.kotlinStdlib.jarRoot)

        val module = module("regular_module")
        module.addDependency(libraryWithAnchor)
        module.addDependency(regularLibrary)
        module.addDependency(stdlib)

        val anchorModule = module("anchor_module")
        val anchorModuleDependency = projectLibrary("AnchorModuleDependency", classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot)
        anchorModule.addDependency(anchorModuleDependency)

        val anchorModuleForStdlib = module("anchor_module_for_stdlib")
        val resolutionAnchorCacheService = ResolutionAnchorCacheService.getInstance(project) as ResolutionAnchorCacheServiceImpl
        val anchorMapping = mapOf(
            libraryWithAnchor.name!! to anchorModule.name,
            stdlib.name!! to anchorModuleForStdlib.name,
        )

        resolutionAnchorCacheService.setAnchors(anchorMapping)

        val libraryWithAnchorInfo = libraryWithAnchor.toLibraryInfo()
        val regularLibraryInfo = regularLibrary.toLibraryInfo()
        val stdlibInfo = stdlib.toLibraryInfo()
        val anchorModuleDependencyInfo = anchorModuleDependency.toLibraryInfo()

        module.production.assertDependenciesEqual(
            module.production,
            libraryWithAnchorInfo,
            regularLibraryInfo,
            stdlibInfo,
        )

        anchorModule.production.assertDependenciesEqual(
            anchorModule.production,
            anchorModuleDependencyInfo,
        )

        anchorModuleForStdlib.production.assertDependenciesEqual(
            anchorModuleForStdlib.production,
        )

        assertDependencies(
            lib = libraryWithAnchorInfo,
            libraryWithAnchorInfo, regularLibraryInfo, stdlibInfo
        )

        assertDependencies(
            lib = regularLibraryInfo,
            libraryWithAnchorInfo, regularLibraryInfo, stdlibInfo
        )

        assertDependencies(
            lib = stdlibInfo,
            stdlibInfo,
        )

        fun assertAnchors(lib: LibraryInfo, expected: Collection<Module>) {
            val anchors = resolutionAnchorCacheService.getDependencyResolutionAnchors(lib)
            assertSortedEquals(
                lib.name.asString(),
                expected.map { it.production },
                actual = anchors,
                renderer = { it.name.asString() },
            )
        }

        project.withLibraryToSourceAnalysis {
            assertAnchors(
                lib = libraryWithAnchorInfo,
                expected = listOf(anchorModule, anchorModuleForStdlib),
            )

            assertAnchors(
                lib = regularLibraryInfo,
                expected = listOf(anchorModule, anchorModuleForStdlib),
            )

            assertAnchors(
                lib = stdlibInfo,
                expected = listOf(anchorModuleForStdlib),
            )
        }
    }

    fun testExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(b)

        a.production.assertDependenciesEqual(a.production)
        b.production.assertDependenciesEqual(b.production, a.production)
        c.production.assertDependenciesEqual(c.production, b.production, a.production)
    }

    fun testRedundantExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        a.production.assertDependenciesEqual(a.production)
        b.production.assertDependenciesEqual(b.production, a.production)
        c.production.assertDependenciesEqual(c.production, a.production, b.production)
    }

    fun testCircularExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(b, exported = true)
        a.addDependency(c, exported = true)

        a.production.assertDependenciesEqual(a.production, c.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production, c.production)
        c.production.assertDependenciesEqual(c.production, b.production, a.production)
    }

    fun testSimpleLibDependency() {
        val a = module("a")
        val lib = projectLibrary()
        a.addDependency(lib)

        a.production.assertDependenciesEqual(a.production, lib.toLibraryInfo())
    }

    fun testCircularExportedDependencyWithLib() {
        val (a, b, c) = modules()

        val lib = projectLibrary()

        a.addDependency(lib)

        b.addDependency(a, exported = true)
        c.addDependency(b, exported = true)
        a.addDependency(c, exported = true)

        b.addDependency(lib)
        c.addDependency(lib)

        a.production.assertDependenciesEqual(a.production, lib.toLibraryInfo(), c.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production, c.production, lib.toLibraryInfo())
        c.production.assertDependenciesEqual(c.production, b.production, a.production, lib.toLibraryInfo())
    }

    fun testSeveralModulesExportLibs() {
        val (a, b, c) = modules()

        val lib1 = projectLibraryWithFakeRoot("lib1")
        val lib2 = projectLibraryWithFakeRoot("lib2")

        a.addDependency(lib1, exported = true)
        b.addDependency(lib2, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib1.toLibraryInfo(), b.production, lib2.toLibraryInfo())
    }

    fun testSeveralModulesExportSameLib() {
        val (a, b, c) = modules()

        val lib = projectLibrary()

        a.addDependency(lib, exported = true)
        b.addDependency(lib, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib.toLibraryInfo(), b.production)
    }

    fun testRuntimeDependency() {
        val (a, b) = modules()

        b.addDependency(a, dependencyScope = DependencyScope.RUNTIME)
        b.addDependency(projectLibrary(), dependencyScope = DependencyScope.RUNTIME)

        b.production.assertDependenciesEqual(b.production)
    }

    fun testProvidedDependency() {
        val (a, b) = modules()
        val lib = projectLibrary()

        b.addDependency(a, dependencyScope = DependencyScope.PROVIDED)
        b.addDependency(lib, dependencyScope = DependencyScope.PROVIDED)

        b.production.assertDependenciesEqual(b.production, a.production, lib.toLibraryInfo())
    }

    fun testSimpleTestDependency() {
        val (a, b) = modules()
        b.addDependency(a, dependencyScope = DependencyScope.TEST)

        a.production.assertDependenciesEqual(a.production)
        a.test.assertDependenciesEqual(a.test, a.production)
        b.production.assertDependenciesEqual(b.production)
        b.test.assertDependenciesEqual(b.test, b.production, a.test, a.production)
    }

    fun testLibTestDependency() {
        val a = module("a")
        val lib = projectLibrary()
        a.addDependency(lib, dependencyScope = DependencyScope.TEST)

        a.production.assertDependenciesEqual(a.production)
        a.test.assertDependenciesEqual(a.test, a.production, lib.toLibraryInfo())
    }

    fun testExportedTestDependency() {
        val (a, b, c) = modules()
        b.addDependency(a, exported = true)
        c.addDependency(b, dependencyScope = DependencyScope.TEST)

        c.production.assertDependenciesEqual(c.production)
        c.test.assertDependenciesEqual(c.test, c.production, b.test, b.production, a.test, a.production)
    }

    fun testDependents() {
        //NOTE: we do not differ between dependency kinds
        val (a, b, c) = modules(name1 = "a", name2 = "b", name3 = "c")
        val (d, e, f) = modules(name1 = "d", name2 = "e", name3 = "f")

        b.addDependency(a, exported = true)

        c.addDependency(a)

        d.addDependency(c, exported = true)

        e.addDependency(b)

        f.addDependency(d)
        f.addDependency(e)


        a.test.assertDependentsEqual(a.test, b.test, c.test, e.test)
        a.production.assertDependentsEqual(a.production, a.test, b.production, b.test, c.production, c.test, e.production, e.test)

        b.test.assertDependentsEqual(b.test, e.test)
        b.production.assertDependentsEqual(b.production, b.test, e.production, e.test)


        c.test.assertDependentsEqual(c.test, d.test, f.test)
        c.production.assertDependentsEqual(c.production, c.test, d.production, d.test, f.production, f.test)

        d.test.assertDependentsEqual(d.test, f.test)
        d.production.assertDependentsEqual(d.production, d.test, f.production, f.test)

        e.test.assertDependentsEqual(e.test, f.test)
        e.production.assertDependentsEqual(e.production, e.test, f.production, f.test)

        f.test.assertDependentsEqual(f.test)
        f.production.assertDependentsEqual(f.production, f.test)
    }

    fun testLibraryDependency1() {
        val lib1 = projectLibraryWithFakeRoot("lib1")
        val lib2 = projectLibraryWithFakeRoot("lib2")

        val module = module("module")
        module.addDependency(lib1)
        module.addDependency(lib2)

        lib1.toLibraryInfo().assertAdditionalLibraryDependencies(lib2.toLibraryInfo())
        lib2.toLibraryInfo().assertAdditionalLibraryDependencies(lib1.toLibraryInfo())
    }

    fun testLibraryDependency2() {
        val lib1 = projectLibraryWithFakeRoot("lib1")
        val lib2 = projectLibraryWithFakeRoot("lib2")
        val lib3 = projectLibraryWithFakeRoot("lib3")

        val (a, b, c) = modules()
        a.addDependency(lib1)
        b.addDependency(lib2)
        c.addDependency(lib3)

        c.addDependency(a)
        c.addDependency(b)

        lib1.toLibraryInfo().assertAdditionalLibraryDependencies()
        lib2.toLibraryInfo().assertAdditionalLibraryDependencies()
        lib3.toLibraryInfo().assertAdditionalLibraryDependencies(lib1.toLibraryInfo(), lib2.toLibraryInfo())
    }

    fun testLibraryDependency3() {
        val lib1 = projectLibraryWithFakeRoot("lib1")
        val lib2 = projectLibraryWithFakeRoot("lib2")
        val lib3 = projectLibraryWithFakeRoot("lib3")

        val (a, b) = modules()
        a.addDependency(lib1)
        b.addDependency(lib2)

        a.addDependency(lib3)
        b.addDependency(lib3)

        lib1.toLibraryInfo().assertAdditionalLibraryDependencies(lib3.toLibraryInfo())
        lib2.toLibraryInfo().assertAdditionalLibraryDependencies(lib3.toLibraryInfo())
        lib3.toLibraryInfo().assertAdditionalLibraryDependencies(lib1.toLibraryInfo(), lib2.toLibraryInfo())
    }

    fun testRoots() {
        val a = module("a", hasProductionRoot = true, hasTestRoot = false)

        val empty = module("empty", hasProductionRoot = false, hasTestRoot = false)
        a.addDependency(empty)

        val b = module("b", hasProductionRoot = false, hasTestRoot = true)
        b.addDependency(a)

        val c = module("c")
        c.addDependency(b)
        c.addDependency(a)

        assertNotNull(a.productionSourceInfo)
        assertNull(a.testSourceInfo)

        assertNull(empty.productionSourceInfo)
        assertNull(empty.testSourceInfo)

        assertNull(b.productionSourceInfo)
        assertNotNull(b.testSourceInfo)

        b.test.assertDependenciesEqual(b.test, a.production)
        c.test.assertDependenciesEqual(c.test, c.production, b.test, a.production)
        c.production.assertDependenciesEqual(c.production, a.production)
    }

    fun testCommonLibraryDoesNotDependOnPlatform() {
        val stdlibCommon = stdlibCommon()
        val stdlibJvm = stdlibJvm()
        val stdlibJs = stdlibJs()
        val stdlibWasm = stdlibWasm()

        val a = module("a")
        a.addDependency(stdlibCommon)
        a.addDependency(stdlibJvm)

        val b = module("b")
        b.setUpPlatform(JsPlatforms.defaultJsPlatform)
        b.addDependency(stdlibCommon)
        b.addDependency(stdlibJs)

        val c = module("c")
        c.setUpPlatform(WasmPlatforms.Default)
        c.addDependency(stdlibCommon)
        c.addDependency(stdlibWasm)

        stdlibCommon.toLibraryInfo().assertAdditionalLibraryDependencies()
        stdlibJvm.toLibraryInfo().assertAdditionalLibraryDependencies(stdlibCommon.toLibraryInfo())
        stdlibJs.toLibraryInfo().assertAdditionalLibraryDependencies(stdlibCommon.toLibraryInfo())
        stdlibWasm.toLibraryInfo().assertAdditionalLibraryDependencies(stdlibCommon.toLibraryInfo())
    }

    fun testScriptDependenciesForModule() {
        val a = module("a")
        val b = module("b")

        with(createFileInModule(a, "script.kts").moduleInfo) {
            dependencies().contains(a.production)
            dependencies().contains(a.test)
            !dependencies().contains(b.production)
        }
    }

    fun testScriptDependenciesForProject() {
        val a = module("a")

        val script = createFileInProject("script.kts").moduleInfo

        !script.dependencies().contains(a.production)
        !script.dependencies().contains(a.test)

        script.dependencies().firstIsInstance<ScriptDependenciesInfo.ForFile>()
    }

    fun testSdkForScript() {
        // The first known jdk will be used for scripting if there is no jdk in the project
        runWriteAction {
            addJdk(testRootDisposable, IdeaTestUtil::getMockJdk16)
            addJdk(testRootDisposable, IdeaTestUtil::getMockJdk9)

            ProjectRootManager.getInstance(project).projectSdk = null
        }

        val allJdks = runReadAction { ProjectJdkTable.getInstance() }.allJdks
        val firstJdk = allJdks.firstOrNull() ?: error("no jdks are present")

        with(createFileInProject("script.kts").moduleInfo) {
            UIUtil.dispatchAllInvocationEvents()
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

            val filterIsInstance = dependencies().filterIsInstance<SdkInfo>()
            filterIsInstance.singleOrNull { it.sdk == firstJdk }
                ?: error("Unable to look up ${firstJdk.name} in ${filterIsInstance.map { it.name }} / allJdks: $allJdks")
        }
    }

    fun testSdkForScriptProjectSdk() {
        val mockJdk16 = IdeaTestUtil.getMockJdk16()
        val mockJdk9 = IdeaTestUtil.getMockJdk9()

        runWriteAction {
            addJdk(testRootDisposable) { mockJdk16 }
            addJdk(testRootDisposable) { mockJdk9 }

            ProjectRootManager.getInstance(project).projectSdk = mockJdk9
        }

        with(createFileInProject("script.kts").moduleInfo) {
            dependencies().filterIsInstance<SdkInfo>().single { it.sdk == mockJdk9 }
        }
    }

    fun testSdkForScriptModuleSdk() {
        val mockJdk16 = IdeaTestUtil.getMockJdk16()
        val mockJdk9 = IdeaTestUtil.getMockJdk9()

        val a = module("a")

        runWriteAction {
            addJdk(testRootDisposable) { mockJdk16 }
            addJdk(testRootDisposable) { mockJdk9 }

            ProjectRootManager.getInstance(project).projectSdk = mockJdk16
            with(ModuleRootManager.getInstance(a).modifiableModel) {
                sdk = mockJdk9
                commit()
            }
        }

        with(createFileInModule(a, "script.kts").moduleInfo) {
            dependencies().filterIsInstance<SdkInfo>().first { it.sdk == mockJdk9 }
        }
    }

    fun testTransitiveLibraryDependency() {
        val a = module("a")
        val b = module("b")

        val projectLibrary = projectLibraryWithFakeRoot("transitiveForB")
        a.addDependency(projectLibrary)

        val classRoot = createFileInProject("libraryClass")
        val l1 = moduleLibrary(
            module = a,
            libraryName = "#1",
            classesRoot = classRoot,
        )
        val l2 = moduleLibrary(
            module = b,
            libraryName = "#1",
            classesRoot = classRoot,
        )
        Assert.assertEquals("Library infos for the module libraries with equal roots are not equal", l1.toLibraryInfo(), l2.toLibraryInfo())

        a.production.assertDependenciesEqual(a.production, projectLibrary.toLibraryInfo(), l1.toLibraryInfo())
        b.production.assertDependenciesEqual(b.production, l2.toLibraryInfo())
        projectLibrary.toLibraryInfo().assertAdditionalLibraryDependencies(l1.toLibraryInfo())

        Assert.assertTrue(
            "Missing transitive dependency on the project library",
            projectLibrary.toLibraryInfo() in b.production.dependencies().closure { it.dependencies() }
        )
    }

    // KTIJ-20815
    fun testPlatformModulesForJvm() {
        val nativePlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val jvmPlatform = JvmPlatforms.jvm8

        val commonMain = module("commonMain", hasTestRoot = false).also {
            it.setUpPlatform(TargetPlatform(nativePlatform.componentPlatforms + jvmPlatform))
        }
        val nativeMain = module("nativeMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(nativePlatform, dependsOnModules = listOf(commonMain))
        }
        val intermediateJvmMain = module("intermediateJvmMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(commonMain))
        }
        val leafJvmMain = module("leafJvmMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(intermediateJvmMain))
        }

        val jvmInfos = getIdeaModelInfosCache(project).forPlatform(jvmPlatform)

        Assert.assertEquals(
            "Exactly one JVM platform module info should be created",
            1, jvmInfos.filterIsInstance<PlatformModuleInfo>().size,
        )
        Assert.assertEquals(
            "JVM platform module info should be created from the leaf JVM module, not shared JVM",
            leafJvmMain.production, jvmInfos.filterIsInstance<PlatformModuleInfo>().single().platformModule,
        )
        Assert.assertTrue(
            "Native module info should remain intact after JVM platform module info creation",
            nativeMain.production in jvmInfos,
        )
    }

    fun testPlatformModulesForNative() {
        val nativePlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val jvmPlatform = JvmPlatforms.jvm8

        val commonMain = module("commonMain", hasTestRoot = false).also {
            it.setUpPlatform(TargetPlatform(nativePlatform.componentPlatforms + jvmPlatform))
        }
        val intermediateJvmMain = module("intermediateJvmMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(commonMain))
        }
        val leafJvmMain = module("leafJvmMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(intermediateJvmMain))
        }
        val nativeMain = module("nativeMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(nativePlatform, dependsOnModules = listOf(commonMain))
        }

        val nativeInfos = getIdeaModelInfosCache(project).forPlatform(nativePlatform)

        Assert.assertEquals(
            "Exactly one Native platform module info should be created",
            1, nativeInfos.filterIsInstance<PlatformModuleInfo>().size,
        )
        Assert.assertEquals(
            "Native platform module info should be created from the native module",
            nativeMain.production, nativeInfos.filterIsInstance<PlatformModuleInfo>().single().platformModule,
        )
        Assert.assertTrue(
            "JVM module infos should remain intact after Native platform module info creation",
            leafJvmMain.production in nativeInfos && intermediateJvmMain.production in nativeInfos,
        )
    }

    fun testPlatformModulesForTwoIndependentModules() {
        val jvmPlatform = JvmPlatforms.jvm8
        val nativePlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)

        module("leafJvmMain", hasTestRoot = false).also{ it.setUpPlatform(jvmPlatform) }
        module("leafNativeMain", hasTestRoot = false).also{ it.setUpPlatform(nativePlatform) }

        val moduleInfos = getIdeaModelInfosCache(project)

        fun assert(platform: TargetPlatform) {
            Assert.assertTrue(
                "Platform module infos should only be created for platform modules with shared common modules",
                moduleInfos.forPlatform(platform).none { it is PlatformModuleInfo }
            )
        }

        assert(jvmPlatform)
        assert(nativePlatform)
    }

    fun testPlatformModulesForSimpleCommon() {
        val jvmPlatform = JvmPlatforms.jvm8
        val nativePlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)

        val common = module("commonMain", hasTestRoot = false).also {
            it.setUpPlatform(TargetPlatform(jvmPlatform.componentPlatforms + nativePlatform))
        }
        val jvmModule = module("leafJvmMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(common))
        }
        val nativeModule = module("leafNativeMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(nativePlatform, dependsOnModules = listOf(common))
        }

        assertPlatformModules(jvmPlatform, jvmModule)
        assertPlatformModules(nativePlatform, nativeModule)
    }

    fun testPlatformModulesForIntermediateCommon() {
        val jvmPlatform = JvmPlatforms.jvm8
        val nativePlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val commonPlatform = TargetPlatform(jvmPlatform.componentPlatforms + nativePlatform)

        val common = module("commonMain", hasTestRoot = false).also { it.setUpPlatform(commonPlatform) }
        val intermediate = module("intermediateMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(commonPlatform, dependsOnModules = listOf(common))
        }
        val jvmModule = module("leafJvmMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(intermediate))
        }
        val nativeModule = module("leafNativeMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(nativePlatform, dependsOnModules = listOf(intermediate))
        }

        assertPlatformModules(jvmPlatform, jvmModule)
        assertPlatformModules(nativePlatform, nativeModule)
    }

    fun testPlatformModulesForDiamond() {
        val thePlatform = JvmPlatforms.jvm8

        val common = module("commonMain", hasTestRoot = false).also { it.setUpPlatform(thePlatform) }
        val left = module("leftMain", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(thePlatform, dependsOnModules = listOf(common))
        }
        val right = module("rightMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(thePlatform, dependsOnModules = listOf(common))
        }
        val leaf = module("leafMain", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(thePlatform, dependsOnModules = listOf(left, right))
        }

        assertPlatformModules(thePlatform, leaf)
    }

    fun testPlatformModulesForForkedTree() {
        val jvmPlatform = JvmPlatforms.jvm8
        val macOsPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val iosPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.IOS_X64)
        val watchosPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.WATCHOS_X64)
        val sharedNativePlatform = TargetPlatform(macOsPlatform.componentPlatforms + iosPlatform + watchosPlatform)
        val commonPlatform = TargetPlatform(sharedNativePlatform.componentPlatforms + jvmPlatform)

        val common = module("common", hasTestRoot = false).also { it.setUpPlatform(commonPlatform) }
        val sharedNative = module("sharedNative", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(sharedNativePlatform, dependsOnModules = listOf(common))
        }
        val watchOs = module("watchOs", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(watchosPlatform, dependsOnModules = listOf(sharedNative))
        }
        val ios = module("ios", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(iosPlatform, dependsOnModules = listOf(sharedNative))
        }
        val macOs = module("macOs", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(macOsPlatform, dependsOnModules = listOf(sharedNative))
        }
        val jvm = module("jvm", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(common))
        }

        assertPlatformModules(jvmPlatform, jvm)
        assertPlatformModules(macOsPlatform, macOs)
        assertPlatformModules(iosPlatform, ios)
        assertPlatformModules(watchosPlatform, watchOs)
    }

    fun testPlatformModulesForBambooBranchedTree() {
        val jvmPlatform = JvmPlatforms.jvm8
        val macOsPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MACOS_X64)
        val iosPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.IOS_X64)
        val sharedNativePlatform = TargetPlatform(macOsPlatform.componentPlatforms + iosPlatform)
        val commonPlatform = TargetPlatform(sharedNativePlatform.componentPlatforms + jvmPlatform)

        val common = module("common", hasTestRoot = false).also { it.setUpPlatform(commonPlatform) }
        val left = module("left", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(sharedNativePlatform, dependsOnModules = listOf(common))
        }
        val leftLeft = module("leftLeft", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(macOsPlatform, dependsOnModules = listOf(left))
        }
        val leftRight = module("leftRight", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(iosPlatform, dependsOnModules = listOf(left))
        }
        val right = module("right", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(common))
        }
        val rightRight = module("farRight", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(right))
        }
        val macOsModule = module("macos", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(macOsPlatform, dependsOnModules = listOf(leftLeft))
        }
        val iosModule = module("ios", hasTestRoot = false).also {
            it.setUpPlatformAndDependsOnModules(iosPlatform, dependsOnModules = listOf(leftRight))
        }
        val jvmModule = module("jvm", hasTestRoot = false).also{
            it.setUpPlatformAndDependsOnModules(jvmPlatform, dependsOnModules = listOf(rightRight))
        }

        assertPlatformModules(jvmPlatform, jvmModule)
        assertPlatformModules(macOsPlatform, macOsModule)
        assertPlatformModules(iosPlatform, iosModule)
    }

    fun testPlatformModulesForTestModules() {
        val thePlatform = JvmPlatforms.jvm8
        val common = module("common", hasTestRoot = true).also { it.setUpPlatform(thePlatform) }
        val intermediate = module("intermediate", hasTestRoot = true).also {
            it.setUpPlatformAndDependsOnModules(thePlatform, dependsOnModules = listOf(common))
        }
        val leaf = module("leaf", hasTestRoot = true).also {
            it.setUpPlatformAndDependsOnModules(thePlatform, dependsOnModules = listOf(intermediate))
        }

        val moduleInfosForThePlatform = getIdeaModelInfosCache(project).forPlatform(thePlatform)

        Assert.assertNotNull(
            "Expect single PlatformModuleInfo for main compilation",
            moduleInfosForThePlatform.singleOrNull { it is PlatformModuleInfo && it.platformModule == leaf.production }
        )

        Assert.assertNotNull(
            "Expect single PlatformModuleInfo for test compilation",
            moduleInfosForThePlatform.singleOrNull { it is PlatformModuleInfo && it.platformModule == leaf.test }
        )

        Assert.assertEquals(
            "Expect only two platform modules, one for main and one for test compilation",
            2, moduleInfosForThePlatform.filterIsInstance<PlatformModuleInfo>().size
        )
    }

    private fun assertPlatformModules(platform: TargetPlatform, leafSourceModule: Module) {
        val moduleInfos = getIdeaModelInfosCache(project)
        val moduleInfosForPlatform = moduleInfos.forPlatform(platform)
        val platformModulesForPlatform = moduleInfosForPlatform.filterIsInstance<PlatformModuleInfo>()

        Assert.assertNotNull(
            "Expect the only platform module for platform $platform to be based on $leafSourceModule; found: ${platformModulesForPlatform}",
            platformModulesForPlatform.singleOrNull()?.takeIf {
                it.platformModule == leafSourceModule.production
            }
        )

        Assert.assertTrue(
            "Expect no unbound modules not covered by a PlatformModuleInfo for platform $platform",
            moduleInfosForPlatform.none { it is ModuleSourceInfo && it.platform == platform }
        )
    }

    private fun createFileInModule(module: Module, fileName: String, inTests: Boolean = false): VirtualFile {
        val fileToCopyIO = createTempFile(fileName, "")

        for (contentEntry in ModuleRootManager.getInstance(module).contentEntries) {
            for (sourceFolder in contentEntry.sourceFolders) {
                if (((!inTests && !sourceFolder.isTestSource) || (inTests && sourceFolder.isTestSource)) && sourceFolder.file != null) {
                    return runWriteAction {
                        getVirtualFile(fileToCopyIO).copy(this, sourceFolder.file!!, fileName)
                    }
                }
            }
        }

        error("Couldn't find source folder in ${module.name}")
    }

    private fun assertDependencies(lib: LibraryInfo, vararg expectedLibraries: LibraryInfo) {
        val dependencies = LibraryDependenciesCache.getInstance(project).getLibraryDependencies(lib)
        assertEqualsLibraries(
            "LibraryInfo '${lib.name}' dependencies",
            expectedLibraries.toList(),
            dependencies.libraries,
        )
    }

    private fun assertEqualsLibraries(message: String, expected: Collection<LibraryInfo>, actual: Collection<LibraryInfo>) {
        assertSortedEquals(
            message = message,
            expected = expected,
            actual = actual,
            renderer = { it.name.asString() },
        )
    }

    private fun <T> assertSortedEquals(message: String, expected: Collection<T>, actual: Collection<T>, renderer: (T) -> String) {
        assertEquals(
            message,
            expected.joinToString(separator = "\n", transform = renderer),
            actual.map(renderer).sorted().joinToString(separator = "\n"),
        )
    }

    private fun assertDependencies(lib: LibraryInfo, expectedSdkInfos: List<SdkInfo>, vararg expectedLibraries: LibraryInfo) {
        val dependencies = LibraryDependenciesCache.getInstance(project).getLibraryDependencies(lib)
        assertEquals(
            "LibraryInfo '${lib.name}' dependencies",
            expectedLibraries.joinToString(separator = "\n") { it.name.asString() },
            dependencies.libraries.map { it.name.asString() }.sorted().joinToString(separator = "\n"),
        )

        assertEquals(
            "LibraryInfo '${lib.name}' sdk dependency",
            expectedSdkInfos.map { it.name.asString() }.sorted().joinToString(separator = "\n"),
            dependencies.sdk.map { it.name.asString() }.sorted().joinToString(separator = "\n"),
        )
    }

    private fun createFileInProject(fileName: String): VirtualFile {
        return runWriteAction {
            getVirtualFile(createTempFile(fileName, "")).copy(this, VfsUtil.findFileByIoFile(File(project.basePath!!), true)!!, fileName)
        }
    }

    private fun Module.addDependency(
        other: Module,
        dependencyScope: DependencyScope = DependencyScope.COMPILE,
        exported: Boolean = false
    ) = ModuleRootModificationUtil.addDependency(this, other, dependencyScope, exported)

    private val VirtualFile.moduleInfo: IdeaModuleInfo
        get() {
            return PsiManager.getInstance(project).findFile(this)!!.moduleInfo
        }

    private val Module.production: ModuleProductionSourceInfo
        get() = productionSourceInfo!!

    private val Module.test: ModuleTestSourceInfo
        get() = testSourceInfo!!

    private fun LibraryEx.toLibraryInfo(): LibraryInfo = LibraryInfoCache.getInstance(project)[this].first()

    private fun Module.toSdkInfo(sdk: ResolveSdk?): SdkInfo? {
        val findOrGetCachedSdk = SdkInfoCache.getInstance(project).findOrGetCachedSdk(this.production)
        if (sdk != null) {
            check(when(sdk) {
                FullJdk -> findOrGetCachedSdk?.sdk == PluginTestCaseBase.jdk(TestJdkKind.FULL_JDK)
                KotlinSdk -> findOrGetCachedSdk?.sdk?.sdkType == KotlinSdkType.INSTANCE
                else -> false
            }) {
                "module '$name' has to have '$sdk' SDK"
            }
        }
        return findOrGetCachedSdk
    }

    private fun module(name: String, hasProductionRoot: Boolean = true, hasTestRoot: Boolean = true): Module {
        return createModuleFromTestData(createTempDirectory().absolutePath, name, StdModuleTypes.JAVA, false).apply {
            if (hasProductionRoot)
                PsiTestUtil.addSourceContentToRoots(this, dir(), false)
            if (hasTestRoot)
                PsiTestUtil.addSourceContentToRoots(this, dir(), true)
        }
    }

    private fun dir() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory())!!

    private fun modules(name1: String = "a", name2: String = "b", name3: String = "c") = Triple(module(name1), module(name2), module(name3))

    private fun IdeaModuleInfo.assertDependenciesEqual(vararg expected: IdeaModuleInfo) {
        Assert.assertEquals(expected.toList(), this.dependencies())
    }

    private fun LibraryInfo.assertAdditionalLibraryDependencies(vararg expected: IdeaModuleInfo) {
        Assert.assertEquals(this, dependencies().first())
        val dependenciesWithoutSelf = this.dependencies().drop(1)
        UsefulTestCase.assertSameElements(dependenciesWithoutSelf, expected.toList())
    }

    private fun ModuleSourceInfo.assertDependentsEqual(vararg expected: ModuleSourceInfo) {
        UsefulTestCase.assertSameElements(this.getDependentModules(), expected.toList())
    }

    private fun stdlibCommon(): LibraryEx = projectLibrary(
        "kotlin-stdlib-common",
        TestKotlinArtifacts.kotlinStdlibCommon.jarRoot,
        kind = KotlinCommonLibraryKind
    )

    private fun stdlibJvm(): LibraryEx = projectLibrary("kotlin-stdlib", TestKotlinArtifacts.kotlinStdlib.jarRoot)

    private fun stdlibJs(): LibraryEx = projectLibrary(
      "kotlin-stdlib-js",
      TestKotlinArtifacts.kotlinStdlibJs.jarRoot,
      kind = KotlinJavaScriptLibraryKind
    )

    private fun stdlibWasm(): LibraryEx = projectLibrary(
        "kotlin-stdlib-wasm",
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(TestKotlinArtifacts.kotlinStdlibWasm),
        kind = KotlinWasmLibraryKind
    )

    private fun projectLibraryWithFakeRoot(name: String): LibraryEx {
        return projectLibrary(name, sourcesRoot = createFileInProject(name))
    }

    private fun Module.setUpPlatform(targetPlatform: TargetPlatform) {
        createMultiplatformFacetM3(
            platformKind = targetPlatform,
            dependsOnModuleNames = listOf(),
            pureKotlinSourceFolders = listOf(),
        )
    }

    private fun Module.setUpPlatformAndDependsOnModules(
        targetPlatform: TargetPlatform,
        dependsOnModules: List<Module>,
    ) {
        createMultiplatformFacetM3(
            platformKind = targetPlatform,
            dependsOnModuleNames = dependsOnModules.map { it.name },
            pureKotlinSourceFolders = listOf(),
        )
    }

    override fun setUp() {
        super.setUp()

        vfsDisposable = allowProjectRootAccess(this)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable { super.tearDown() },
        )
    }
}
