// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
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
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.caches.project.getDependentModules
import org.jetbrains.kotlin.idea.caches.project.getIdeaModelInfosCache
import org.jetbrains.kotlin.idea.stubs.createMultiplatformFacetM3
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase.addJdk
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.test.util.addDependency
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.moduleLibrary
import org.jetbrains.kotlin.test.util.projectLibrary
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class IdeaModuleInfoTest8 : JavaModuleTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null

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

        a.production.assertDependenciesEqual(a.production, lib.classes)
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

        a.production.assertDependenciesEqual(a.production, lib.classes, c.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production, c.production, lib.classes)
        c.production.assertDependenciesEqual(c.production, b.production, a.production, lib.classes)
    }

    fun testSeveralModulesExportLibs() {
        val (a, b, c) = modules()

        val lib1 = projectLibraryWithFakeRoot("lib1")
        val lib2 = projectLibraryWithFakeRoot("lib2")

        a.addDependency(lib1, exported = true)
        b.addDependency(lib2, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib1.classes, b.production, lib2.classes)
    }

    fun testSeveralModulesExportSameLib() {
        val (a, b, c) = modules()

        val lib = projectLibrary()

        a.addDependency(lib, exported = true)
        b.addDependency(lib, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib.classes, b.production)
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

        b.production.assertDependenciesEqual(b.production, a.production, lib.classes)
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
        a.test.assertDependenciesEqual(a.test, a.production, lib.classes)
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

        lib1.classes.assertAdditionalLibraryDependencies(lib2.classes)
        lib2.classes.assertAdditionalLibraryDependencies(lib1.classes)
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

        lib1.classes.assertAdditionalLibraryDependencies()
        lib2.classes.assertAdditionalLibraryDependencies()
        lib3.classes.assertAdditionalLibraryDependencies(lib1.classes, lib2.classes)
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

        lib1.classes.assertAdditionalLibraryDependencies(lib3.classes)
        lib2.classes.assertAdditionalLibraryDependencies(lib3.classes)
        lib3.classes.assertAdditionalLibraryDependencies(lib1.classes, lib2.classes)
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

        val a = module("a")
        a.addDependency(stdlibCommon)
        a.addDependency(stdlibJvm)

        val b = module("b")
        b.setUpPlatform(JsPlatforms.defaultJsPlatform)
        b.addDependency(stdlibCommon)
        b.addDependency(stdlibJs)

        stdlibCommon.classes.assertAdditionalLibraryDependencies()
        stdlibJvm.classes.assertAdditionalLibraryDependencies(stdlibCommon.classes)
        stdlibJs.classes.assertAdditionalLibraryDependencies(stdlibCommon.classes)
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
        Assert.assertEquals("Library infos for the module libraries with equal roots are not equal", l1.classes, l2.classes)

        a.production.assertDependenciesEqual(a.production, projectLibrary.classes, l1.classes)
        b.production.assertDependenciesEqual(b.production, l2.classes)
        projectLibrary.classes.assertAdditionalLibraryDependencies(l1.classes)

        Assert.assertTrue(
            "Missing transitive dependency on the project library",
            projectLibrary.classes in b.production.dependencies().closure { it.dependencies() }
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

    private val LibraryEx.classes: LibraryInfo
        get() = object : LibraryInfo(project!!, this) {
            override val platform: TargetPlatform
                get() = kind.platform
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
