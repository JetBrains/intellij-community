// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScope
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.requireIs
import com.intellij.util.CommonProcessors
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.platform.modification.*
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.util.compileScriptsIntoDirectory
import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.moduleLibrary
import org.jetbrains.kotlin.test.util.projectLibrary
import org.junit.Assert
import java.io.File

class KotlinProjectStructureTest : AbstractMultiModuleTest() {

    override fun getTestProjectJdk(): Sdk = IdeaTestUtil.getMockJdk11()

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getTestDataDirectory(): File = throw UnsupportedOperationException()

    override fun setUp() {
        super.setUp()

        val testProjectStructureInsightsProvider = object : ProjectStructureInsightsProvider {
            override fun isInSpecialSrcDirectory(psiElement: PsiElement): Boolean {
                if (!RootKindFilter.Companion.projectSources.matches(psiElement)) return false
                val containingFile = psiElement.containingFile as? KtFile ?: return false
                val virtualFile = containingFile.virtualFile
                val index = ProjectFileIndex.getInstance(psiElement.project)
                val module = index.getModuleForFile(virtualFile) ?: return false
                return module.name == "buildSrc"
            }
        }

        ExtensionTestUtil.maskExtensions(
          ProjectStructureInsightsProvider.Companion.EP_NAME,
          ProjectStructureInsightsProvider.Companion.EP_NAME.extensionList +
          listOf(testProjectStructureInsightsProvider),
          testRootDisposable
        )
    }

    fun `test unrelated library`() {
        val moduleWithLibrary = createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
              dir("one") {
                file("A.kt", "class A")
              }
            }
        )

        val libraryName = "lib"
        moduleLibrary(
          moduleWithLibrary,
          libraryName = libraryName,
          classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
          sourcesRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot,
        )

        createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
              dir("one") {
                file("Main.kt", "class Main")
              }
            }
        )

        val sourceFile = getFile("Main.kt")
        val sourceModule = kaModuleWithAssertion<KaSourceModule>(sourceFile)

        val libraryClassFile = getFile("KotlinCompileDaemon.class")
        val libraryModuleWithoutContext = kaModuleWithAssertion<KaLibraryModule>(libraryClassFile)
        assertEquals(libraryName, libraryModuleWithoutContext.libraryName)

        val libraryModuleWithUnrelatedContext = kaModuleWithAssertion<KaLibraryModule>(libraryClassFile, sourceModule)
        assertEquals(libraryModuleWithoutContext, libraryModuleWithUnrelatedContext)

        val librarySourceFile = getFile("_Collections.kt")
        val librarySourceModuleWithoutContext = kaModuleWithAssertion<KaLibrarySourceModule>(librarySourceFile)
        assertEquals(libraryModuleWithoutContext.librarySources, librarySourceModuleWithoutContext)

        val librarySourceModuleWithUnrelatedContext = kaModuleWithAssertion<KaLibrarySourceModule>(librarySourceFile, sourceModule)
        assertEquals(librarySourceModuleWithoutContext, librarySourceModuleWithUnrelatedContext)
    }

    fun `test module library`() {
        val moduleA = createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
              dir("one") {
                dir("two") {
                  file("Main.kt", "class Main")
                }
              }
            }
        )

        val libraryName = "module_library"
        val library = moduleLibrary(
          moduleA,
          libraryName = libraryName,
          classesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot,
          sourcesRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot,
        )

        val kotlinReflectLibraryName = "kotlin reflect"
        val kotlinReflectLibrary = moduleLibrary(
          moduleA,
          libraryName = kotlinReflectLibraryName,
          classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot,
          sourcesRoot = TestKotlinArtifacts.kotlinReflectSources.jarRoot,
        )

        val sourceFile = getFile("Main.kt")

        val libraryScope = LibraryScope(project, library)
        val libraryFile = getFile("KotlinCompileDaemon.class", libraryScope)

        val libraryModuleWithoutContext = kaModuleWithAssertion<KaLibraryModule>(libraryFile)
        assertEquals(libraryName, libraryModuleWithoutContext.libraryName)

        val sourceModule = kaModuleWithAssertion<KaSourceModule>(sourceFile)
        val libraryModuleWithContext = kaModuleWithAssertion<KaLibraryModule>(libraryFile, contextualModule = sourceModule)

        assertEquals(libraryModuleWithoutContext, libraryModuleWithContext)
        assertTrue("The library module must be in dependencies", libraryModuleWithContext in sourceModule.directRegularDependencies)

        val libSourceFile = getFile("Comparator.kt")
        val libSourceModule = kaModuleWithAssertion<KaLibrarySourceModule>(libSourceFile)
        assertEquals(libraryModuleWithoutContext.librarySources, libSourceModule)

        val reflectionLibSourceFile = getFile("FunctionWithAllInvokes.kt")
        val reflectionLibSourceModule = kaModuleWithAssertion<KaLibrarySourceModule>(reflectionLibSourceFile)
        assertEquals(kotlinReflectLibraryName, reflectionLibSourceModule.libraryName)

        val libModuleForBinaryFile = kaModuleWithAssertion<KaLibraryModule>(libraryFile, contextualModule = libSourceModule)
        assertEquals(libraryModuleWithoutContext, libModuleForBinaryFile)

        val libraryModule = kaModuleWithAssertion<KaLibraryModule>(libraryFile, contextualModule = reflectionLibSourceModule)
        assertEquals(libraryName, libraryModule.libraryName)
    }

    fun `test module libraries with the same shared content`() {
        val sharedLibraryContentRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot
        val moduleA = createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
              dir("one") {
                file("A.kt", "class A")
              }
            }
        )

        val libraryAName = "module_library_a"
        val libraryA = moduleLibrary(
            moduleA,
            libraryName = libraryAName,
            classesRoot = sharedLibraryContentRoot,
        )

        val moduleB = createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
              dir("one") {
                file("B.kt", "class B")
              }
            }
        )

        val sharedLibrarySourceContentRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot
        val libraryBName = "module_library_b"
        val libraryB = moduleLibrary(
            moduleB,
            libraryName = libraryBName,
            classesRoot = sharedLibraryContentRoot,
            sourcesRoot = sharedLibrarySourceContentRoot,
        )

        val libraryCName = "module_library_c"
        val libraryC = moduleLibrary(
          moduleA,
          libraryName = libraryCName,
          classesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot,
          sourcesRoot = sharedLibrarySourceContentRoot,
        )

        val sourceAFile = getFile("A.kt")
        val sourceBFile = getFile("B.kt")

        val libraryAScope = LibraryScope(project, libraryA)
        val libraryBScope = LibraryScope(project, libraryB)

        val libraryAFile = getFile("KotlinCompileDaemon.class", libraryAScope)
        val libraryBFile = getFile("KotlinCompileDaemon.class", libraryBScope)
        assertEquals("Files from libraries A and B must be the same due to sharing", libraryAFile, libraryBFile)

        // -------> A module logic (the first one in the order)
        val libraryAModuleWithoutContext = kaModuleWithAssertion<KaLibraryModule>(libraryAFile)
        assertEquals(libraryAName, libraryAModuleWithoutContext.libraryName)

        val sourceAModule = kaModuleWithAssertion<KaSourceModule>(sourceAFile)
        val libraryAModuleWithContext = kaModuleWithAssertion<KaLibraryModule>(libraryAFile, contextualModule = sourceAModule)
        assertEquals(libraryAModuleWithoutContext, libraryAModuleWithContext)
        assertTrue("The library module must be in dependencies", libraryAModuleWithContext in sourceAModule.directRegularDependencies)

        // -------> B module logic (the last one in the order)
        val libraryBModuleWithoutContext = kaModuleWithAssertion<KaLibraryModule>(libraryBFile)
        assertEquals(
            "The library name should be from the first module due to a context absence",
            libraryAName,
            libraryBModuleWithoutContext.libraryName,
        )

        val sourceBModule = kaModuleWithAssertion<KaSourceModule>(sourceBFile)
        val libraryBModuleWithContext = kaModuleWithAssertion<KaLibraryModule>(libraryBFile, contextualModule = sourceBModule)
        assertEquals(
            "The library name must be from the corresponding module if a context passed",
            libraryBName,
            libraryBModuleWithContext.libraryName,
        )

      Assert.assertNotEquals(
        "The library module must be from the corresponding module if a context passed",
        libraryBModuleWithoutContext,
        libraryBModuleWithContext,
      )

        assertTrue("The library module must be in dependencies", libraryBModuleWithContext in sourceBModule.directRegularDependencies)

        val libraryCFile = getFile("KProperties.class")
        val libraryCModule = kaModuleWithAssertion<KaLibraryModule>(libraryCFile)
        assertEquals(libraryCName, libraryCModule.libraryName)

        val sharedLibrarySourceFile = getFile("_Collections.kt")
        val sharedLibrarySourceModuleWithoutContext = kaModuleWithAssertion<KaLibrarySourceModule>(sharedLibrarySourceFile)
        val libraryBModule = libraryB.toKaModule()
        assertEquals(
            "The library source should be from the first module due to a context absence",
            libraryBModule.librarySources!!,
            sharedLibrarySourceModuleWithoutContext,
        )

        assertEquals(
            "The library source must be the same due to the same context as the first library",
            libraryBModule.librarySources!!,
            kaModuleWithAssertion<KaLibrarySourceModule>(sharedLibrarySourceFile, moduleA.toKaSourceModule(KaSourceModuleKind.PRODUCTION)),
        )

        val sharedLibrarySourceModuleWithBContext = kaModuleWithAssertion<KaLibrarySourceModule>(sharedLibrarySourceFile, sourceBModule)
        Assert.assertEquals(
            "The library source module must be from the corresponding module if a context passed",
            libraryBModule.librarySources!!,
            sharedLibrarySourceModuleWithBContext,
        )

        assertEquals(libraryBModuleWithContext.librarySources, sharedLibrarySourceModuleWithBContext)
    }

    fun `test project libraries with shared classes root search in context of transitively dependent module`() {
        val sharedClassesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot

        // Names are important – the unrelated library must be the first one to be met
        val libraryUnrelated = projectLibrary(
            "lib_for_a",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot,
        )
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("A.kt", "class A")
                }
            }
        ).apply { addDependency(libraryUnrelated) }

        val libraryForB = projectLibrary(
            "lib_for_b",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot,
        )
        val moduleB = createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("B.kt", "class B")
                }
            }
        )
        val moduleC = createModule(
            moduleName = "c",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("C.kt", "class C")
                }
            }
        )
        moduleB.addDependency(libraryForB, exported = true)
        moduleC.addDependency(moduleB)

        val kaModuleC = kaModuleWithAssertion<KaSourceModule>(getFile("C.kt"))
        val foundLib = kaModuleWithAssertion<KaLibraryModule>(getFile("KotlinCompileDaemon.class"), contextualModule = kaModuleC)
        assertEquals(libraryForB.name, foundLib.libraryName)
    }

    fun `test when searching transitive lib consider isExported flag on lib`() {
        val sharedClassesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot

        // Names are important – the unrelated library must be the first one to be met
        val libraryUnrelated = projectLibrary(
            "lib_for_a",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot,
        )
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("Unrelated.kt", "class Unrelated")
                }
            }
        ).apply { addDependency(libraryUnrelated, exported = false) }

        val libraryForB = projectLibrary(
            "lib_for_b",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot,
        )
        val moduleB = createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("B.kt", "class B")
                }
            }
        )
        val moduleC = createModule(
            moduleName = "c",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("C.kt", "class C")
                }
            }
        )
        moduleB.addDependency(libraryForB, exported = false)
        moduleC.addDependency(moduleB)

        val kaModuleC = kaModuleWithAssertion<KaSourceModule>(getFile("C.kt"))
        val libraryModule = kaModuleWithAssertion<KaLibraryModule>(getFile("KotlinCompileDaemon.class"), contextualModule = kaModuleC)

        assertEquals(libraryUnrelated.name, libraryModule.libraryName)
    }

    fun `test when searching transitive lib consider isExported flag on module`() {
        val sharedClassesRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot

        // Names are important – the unrelated library must be the first one to be met
        val libraryUnrelated = projectLibrary(
            "lib_for_a",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinStdlibSources.jarRoot,
        )
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("Unrelated.kt", "class Unrelated")
                }
            }
        ).apply { addDependency(libraryUnrelated, exported = false) }

        val libraryForB = projectLibrary(
            "lib_for_b",
            classesRoot = sharedClassesRoot,
            sourcesRoot = TestKotlinArtifacts.kotlinReflect.jarRoot,
        )
        val moduleB = createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("B.kt", "class B")
                }
            }
        )
        val moduleC = createModule(
            moduleName = "c",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("C.kt", "class C")
                }
            }
        )
        val moduleD = createModule(
            moduleName = "d",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("D.kt", "class D")
                }
            }
        )
        moduleB.addDependency(libraryForB, exported = true)
        moduleC.addDependency(moduleB, exported = false)
        moduleD.addDependency(moduleC)

        val kaModuleD = kaModuleWithAssertion<KaSourceModule>(getFile("D.kt"))
        val libraryModule = kaModuleWithAssertion<KaLibraryModule>(getFile("KotlinCompileDaemon.class"), contextualModule = kaModuleD)

        assertEquals(libraryUnrelated.name, libraryModule.libraryName)
    }

    fun `test source module`() {
        createModule(
          moduleName = "a",
          srcContentSpec = directoryContent {
            dir("one") {
              dir("two") {
                file("Main.kt", "class Main")
              }
            }
          },
          testContentSpec = directoryContent {
            dir("three") {
              file("Test.kt", "class Test")
            }
          },
        )

        assertKaModuleType<KaSourceModule>("Main.kt")
        assertKaModuleType<KaSourceModule>("Test.kt")
    }

    fun `test KaSourceModule cache invalidation`() {
        val main = createModule(
            moduleName = "main",
            srcContentSpec = directoryContent {
                dir("one") { file("Main.kt", "class Main") }
            }
        )

        val dependents = createSimpleSourceModules(count = 3)
        for (dependent in dependents) {
            dependent.module.addDependency(main)
        }

        fun doTest(
            shouldBeInvalidated: Boolean,
            shouldDependentsBeInvalidated: Boolean = shouldBeInvalidated,
            createEvent: (KaModule) -> KotlinModificationEvent,
        ) {
            doInvalidationModuleTest(
                shouldBeInvalidated,
                shouldDependentsBeInvalidated,
                getModuleByFileFromThatModule = {
                    getFile("Main.kt").getKaModuleOfType<KaSourceModule>(project, useSiteModule = null)
                },
                getDependentModulesByCorrespondingPsiFiles = {
                    dependents.map { it.getter() }
                },
                createEvent = createEvent,
            )
        }


        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.REMOVAL) }
        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.UPDATE) }
        doTest(shouldBeInvalidated = true) { KotlinGlobalModuleStateModificationEvent }
        doTest(shouldBeInvalidated = true) { KotlinGlobalSourceModuleStateModificationEvent }

        doTest(shouldBeInvalidated = false) { KotlinModuleOutOfBlockModificationEvent(it) }
        doTest(shouldBeInvalidated = false) { KotlinGlobalScriptModuleStateModificationEvent }
        doTest(shouldBeInvalidated = false) { KotlinGlobalSourceOutOfBlockModificationEvent }
    }


    private data class ModuleWithGetterByFile(
        val module: Module,
        val getter: () -> KaSourceModule,
    )

    fun `test KaLibraryModule library cache invalidation`() {
        val lib = projectLibrary(
            classesRoot = TestKotlinArtifacts.kotlinStdlib.jarRoot,
        )

        val dependents = createSimpleSourceModules(count = 3)
        for (dependent in dependents) {
            dependent.module.addDependency(lib)
        }

        fun doTest(
            shouldBeInvalidated: Boolean,
            shouldDependentsBeInvalidated: Boolean = shouldBeInvalidated,
            createEvent: (KaModule) -> KotlinModificationEvent,
        ) {
            doInvalidationModuleTest(
                shouldBeInvalidated,
                shouldDependentsBeInvalidated,
                getModuleByFileFromThatModule = {
                    val file = JavaPsiFacade.getInstance(project)
                        .findClass("kotlin.Pair", GlobalSearchScope.everythingScope(project))
                        ?: error("Can't find class 'kotlin.Pair'")

                    file.getKaModuleOfType<KaLibraryModule>(project, useSiteModule = null)
                },
                getDependentModulesByCorrespondingPsiFiles = {
                    dependents.map { it.getter() }
                },
                createEvent = createEvent,
            )
        }

        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.REMOVAL) }
        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.UPDATE) }
        doTest(shouldBeInvalidated = true) { KotlinGlobalModuleStateModificationEvent }
        doTest(shouldBeInvalidated = false, shouldDependentsBeInvalidated = true) { KotlinGlobalSourceModuleStateModificationEvent }

        doTest(shouldBeInvalidated = false) { KotlinModuleOutOfBlockModificationEvent(it) }
        doTest(shouldBeInvalidated = false) { KotlinGlobalScriptModuleStateModificationEvent }
        doTest(shouldBeInvalidated = false) { KotlinGlobalSourceOutOfBlockModificationEvent }
    }

    fun `test KaLibraryModule sdk cache invalidation`() {
        val jdk = IdeaTestUtil.getMockJdk17("JDK")
        runWriteAction {
            ProjectJdkTable.getInstance().addJdk(jdk, testRootDisposable)
        }
        val dependents = createSimpleSourceModules(count = 3)
        for (dependent in dependents) {
            ModuleRootModificationUtil.modifyModel(dependent.module) {
                it.sdk = jdk
                true
            }
        }

        fun doTest(
            shouldBeInvalidated: Boolean,
            shouldDependentsBeInvalidated: Boolean = shouldBeInvalidated,
            createEvent: (KaModule) -> KotlinModificationEvent,
        ) {
            doInvalidationModuleTest(
                shouldBeInvalidated,
                shouldDependentsBeInvalidated,
                getModuleByFileFromThatModule = {
                    val file = JavaPsiFacade.getInstance(project).findClass("java.lang.String", GlobalSearchScope.allScope(project))
                        ?: error("Can't find class 'java.lang.String'")

                    file.getKaModuleOfType<KaLibraryModule>(project, useSiteModule = null)
                },
                getDependentModulesByCorrespondingPsiFiles = {
                    dependents.map { it.getter() }
                },
                createEvent = createEvent,
            )
        }

        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.REMOVAL) }
        doTest(shouldBeInvalidated = true) { KotlinModuleStateModificationEvent(it, KotlinModuleStateModificationKind.UPDATE) }
        doTest(shouldBeInvalidated = true) { KotlinGlobalModuleStateModificationEvent }
        doTest(shouldBeInvalidated = false, shouldDependentsBeInvalidated = true) { KotlinGlobalSourceModuleStateModificationEvent }

        doTest(shouldBeInvalidated = false) { KotlinModuleOutOfBlockModificationEvent(it) }
        doTest(shouldBeInvalidated = false) { KotlinGlobalScriptModuleStateModificationEvent }
        doTest(shouldBeInvalidated = false) { KotlinGlobalSourceOutOfBlockModificationEvent }
    }

    private fun createSimpleSourceModules(count: Int): List<ModuleWithGetterByFile> {
        require(count > 0) { "The count must be greater than zero" }
        return (0 until count).map { i ->
            val className = "Class$i"
            val fileName = "$className.kt"
            val module = createModule(
                moduleName = "sourceModule$i",
                srcContentSpec = directoryContent {
                    dir("one") { file(fileName, "class $className") }
                }
            )
            val getter: () -> KaSourceModule = ({
                getFile(fileName).getKaModuleOfType<KaSourceModule>(project, useSiteModule = null)
            })
            ModuleWithGetterByFile(module, getter)
        }
    }


    private fun doInvalidationModuleTest(
        shouldBeInvalidated: Boolean,
        shouldDependentsBeInvalidated: Boolean,
        getModuleByFileFromThatModule: () -> KaModule,
        getDependentModulesByCorrespondingPsiFiles: () -> List<KaModule>,
        createEvent: (KaModule) -> KotlinModificationEvent
    ) {
        val initialKaModule = getModuleByFileFromThatModule()
        assertSame(initialKaModule, getModuleByFileFromThatModule())

        val initialDependentModules = getDependentModulesByCorrespondingPsiFiles()

        val event = createEvent(initialKaModule)
        runWriteActionAndWait {
            project.publishModificationEvent(event)
        }
        val moduleAfter = getModuleByFileFromThatModule()

        fun check(initial: KaModule, after: KaModule, shouldBeInvalidated: Boolean, moduleDescription: String) {
            if (shouldBeInvalidated) {
                assertNotSame(
                    "Expected that ${moduleDescription} IS invalidated but they ARE the same. Event: $event. Initial module: $initial. Module after: $after",
                    initial,
                    after,
                )
            } else {
                assertSame(
                    "Expected that ${moduleDescription} IS NOT invalidated but they ARE NOT the same. Event: $event. Initial module: $initial. Module after: $after",
                    initial,
                    after,
                )
            }
        }

        check(initialKaModule, moduleAfter, shouldBeInvalidated, "main module")

        val dependentModulesAfter = getDependentModulesByCorrespondingPsiFiles()
        check(initialDependentModules.size == dependentModulesAfter.size) {
            "Expected that number of dependent modules is the same. Initial dependent modules: $initialDependentModules. Dependent modules after: $dependentModulesAfter"
        }
        for (i in initialDependentModules.indices) {
            val initial = initialDependentModules[i]
            val after = dependentModulesAfter[i]
            check(initial, after, shouldDependentsBeInvalidated, "dependent module")
        }
    }

    @OptIn(K1ModeProjectStructureApi::class)
    fun `test different jdks attached to project modules`() {
        val mockJdkA = IdeaTestUtil.getMockJdk17("module A JDK")
        val mockJdkB = IdeaTestUtil.getMockJdk17("module B JDK")

      runWriteAction {
        ProjectJdkTable.getInstance().addJdk(mockJdkA, testRootDisposable)
        ProjectJdkTable.getInstance().addJdk(mockJdkB, testRootDisposable)
      }

        val moduleA = createModule(moduleName = "a", srcContentSpec = directoryContent {})
        ModuleRootModificationUtil.modifyModel(moduleA) {
            it.sdk = mockJdkA
            true
        }

        val moduleB = createModule(moduleName = "b", srcContentSpec = directoryContent {})
        ModuleRootModificationUtil.modifyModel(moduleB) {
            it.sdk = mockJdkB
            true
        }

        //a class from "jdk" which belongs to 2 order entries
        val stringClass = JavaPsiFacade.getInstance(project).findClass("java.lang.String", GlobalSearchScope.allScope(project))!!

        val contextualModuleB = moduleB.toKaSourceModuleForProduction()!!
        val module1 = stringClass.getKaModuleOfTypeSafe<KaLibraryModule>(project, contextualModuleB)

        assertNotNull("KaLibraryModule not found for module B", module1)

        val contextualModuleA = moduleA.toKaSourceModuleForProduction()!!
        val module2 = stringClass.getKaModuleOfTypeSafe<KaLibraryModule>(project, contextualModuleA)

        assertNotNull("KaLibraryModule not found for module A", module2)

        assertNotSame("Different jdks are attached to modules, but one jdk is found by search", module1!!.openapiSdk, module2!!.openapiSdk)
    }

    fun `test that buildSrc sources belong to KaSourceModule`() {
        createModule(
            moduleName = "buildSrc",
            srcContentSpec = directoryContent {
              dir("main") {
                dir("kotlin") {
                  file("utils.kt", "fun callMeFromKtsFile() {}")
                }
              }
            }
        )

        createModule(
            moduleName = "utils",
            srcContentSpec = directoryContent {
              file("build.gradle.kts", "callMeFromKtsFile()")
            }
        )

        val dependency = getFile("utils.kt")
        val scriptFile = getFile("build.gradle.kts")

        ScriptConfigurationManager.Companion.updateScriptDependenciesSynchronously(scriptFile)

        assertKaModuleType<KaSourceModule>(dependency, kaModule(scriptFile))
    }

    @OptIn(KaExperimentalApi::class)
    fun `test that script in buildSrc belong to KaScriptModule`() {
        createModule(
            moduleName = "buildSrc",
            srcContentSpec = directoryContent {
              file("build.gradle.kts", "")
            }
        )

        val scriptFile = getFile("build.gradle.kts")
        ScriptConfigurationManager.Companion.updateScriptDependenciesSynchronously(scriptFile)
        assertKaModuleType<KaScriptModule>(scriptFile, kaModule(scriptFile))
    }

    fun `test module of source directory`() {
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
              dir("one") {
                dir("two") {
                  file("Main.kt", "class Main")
                }
              }
            }
        )

        val file = getFile("Main.kt")
        val psiDirectory = file.parent as PsiDirectory

        assertKaModuleType<KaSourceModule>(psiDirectory)
    }

    fun `test out of content module`() {
        val dummyRoot = directoryContent { file("dummy.kt", "class A") }
            .generateInVirtualTempDir()

        val dummyVirtualFile = dummyRoot.findChild("dummy.kt")!!
        val dummyFile = PsiManager.getInstance(project).findFile(dummyVirtualFile)!!

        assertKaModuleType<KaNotUnderContentRootModule>(dummyFile)

        createModule(
          moduleName = "m",
          resourceContentSpec = directoryContent {
            dir("wd") {
              file("resource.kt", "class B")
            }
          },
        )

        // KTIJ-26841
        assertKaModuleType<KaNotUnderContentRootModule>("resource.kt")
    }

    fun `test dangling file module`() {
        val file = createDummyFile("dummy.kt", "class A")
        assertKaModuleType<KaDanglingFileModule>(file)
    }

    fun `test dangling script file module`() {
        val file = createDummyFile("dummy.kts", "class A")
        assertKaModuleType<KaDanglingFileModule>(file)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test dangling file element cannot be analyzed in context module scope`() {
        // See KT-71135.
        createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("A.kt", "class A")
                }
            }
        )
        val sourceKtFile = getFile("A.kt")
        val sourceKaModule = kaModuleWithAssertion<KaSourceModule>(sourceKtFile)

        val temporaryFile = KtPsiFactory.contextual(sourceKtFile).createFile(name, "A()")
        temporaryFile.originalFile = sourceKtFile
        assertKaModuleType<KaDanglingFileModule>(temporaryFile)

        allowAnalysisOnEdt {
            analyze(sourceKaModule) {
                assertFalse(
                    "The dangling file should not be analyzable in the context of its source module.",
                    temporaryFile.canBeAnalysed(),
                )
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    fun `test script module`() {
        val dummyRoot = directoryContent { file("dummy.kts", "class A") }
            .generateInVirtualTempDir()

        val dummyVirtualFile = dummyRoot.findChild("dummy.kts")!!
        val dummyFile = PsiManager.getInstance(project).findFile(dummyVirtualFile)!!

        assertKaModuleType<KaScriptModule>(dummyFile)

        createModule(
          moduleName = "m",
          resourceContentSpec = directoryContent {
            dir("wd") {
              file("myScript.kts", "class B")
            }
          },
        )

        assertKaModuleType<KaScriptModule>("myScript.kts")
    }

    fun `test compiled script class always belongs to KaLibraryModule`() {
        val fileStructure = directoryContent {
            dir("test") {
                file("A.kts",
                     """
                     fun foo() = 42
                     foo()
                 """.trimIndent()
                )
            }

            dir("toCompile") {
                file("Simple.kts", "class Simple {}")
            }

            dir("compiled") {
            }
        }

        val rootFile = fileStructure.generateInVirtualTempDir()
        val scriptModule = createModule(moduleName ="scriptA", resourceContentSpec = fileStructure)
        val compiledScriptsDirectory = rootFile.findChild("compiled")!!

        val scriptFile = VfsUtil.virtualToIoFile(rootFile.findFile("toCompile/Simple.kts")!!)
        /*
        To store compiled script artifacts,
        one should use 'KotlinCompilerStandalone' directly.
         */
        compiledScriptsDirectory.compileScriptsIntoDirectory(listOf(scriptFile))
        compiledScriptsDirectory.refresh(false, true)

        moduleLibrary(
            scriptModule,
            libraryName = "libWithCompiledScripts",
            classesRoot = compiledScriptsDirectory,
        )

        val scriptPsiFile = (getFile("A.kts") as KtFile).script as KtScript
        val compiledScriptPsiFile = getFile("Simple.class")

        assertKaModuleType<KaLibraryModuleImpl>(
            compiledScriptPsiFile,
            kaModule(scriptPsiFile)
        )
        assertKaModuleType<KaLibraryModuleImpl>(
            compiledScriptPsiFile
        )
    }

    fun `test element to library mapping consistency with contextual library module`() {
        val firstStdlibLibrary = projectLibrary(
          "kotlin-stdlib-first",
          TestKotlinArtifacts.kotlinStdlib.jarRoot,
          TestKotlinArtifacts.kotlinStdlibSources.jarRoot
        )

        val secondStdlibLibrary = projectLibrary(
          "kotlin-stdlib-second",
          TestKotlinArtifacts.kotlinStdlib.jarRoot,
          TestKotlinArtifacts.kotlinStdlibSources.jarRoot
        )

        val kotlinReflectLibrary = projectLibrary(
          "kotlin-reflect",
          TestKotlinArtifacts.kotlinReflect.jarRoot,
          TestKotlinArtifacts.kotlinReflectSources.jarRoot
        )

        val firstModule = createModule("first").apply { addDependency(firstStdlibLibrary) }
        val secondModule = createModule("second").apply { addDependency(secondStdlibLibrary) }
        val thirdModule = createModule("third").apply { addDependency(kotlinReflectLibrary) }

        fun testClassModule(className: String, module: Module, expectedLibrary: Library, contextLibrary: Library = expectedLibrary) {
            val expectedLibraryKtModule = expectedLibrary.toKaModule()
            val expectedLibrarySourceKtModule = expectedLibraryKtModule.librarySources!!

            val contextLibraryKtModule = contextLibrary.toKaModule()
            val contextLibrarySourceKtModule = contextLibraryKtModule.librarySources!!

            val javaLightClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.moduleWithLibrariesScope(module)) as KtLightClass

            val kotlinDecompiledClass = javaLightClass.kotlinOrigin!!
            assert(kotlinDecompiledClass.containingKtFile.isCompiled)

            val actualLibraryKtModule = KotlinProjectStructureProvider.Companion.getModule(
                project,
                kotlinDecompiledClass,
                useSiteModule = contextLibraryKtModule,
            )

            assertEquals(expectedLibraryKtModule, actualLibraryKtModule)

            val kotlinSourceClass = service<KotlinDeclarationNavigationPolicy>().getNavigationElement(kotlinDecompiledClass)
            assertFalse(kotlinSourceClass.containingKtFile.isCompiled)

            val actualLibrarySourceKtModule = KotlinProjectStructureProvider.Companion.getModule(
                project,
                kotlinSourceClass,
                useSiteModule = contextLibrarySourceKtModule,
            )

            assertEquals(expectedLibrarySourceKtModule, actualLibrarySourceKtModule)
        }

        testClassModule("kotlin.KotlinVersion", firstModule, firstStdlibLibrary)
        testClassModule("kotlin.KotlinVersion", secondModule, secondStdlibLibrary)
        testClassModule("kotlin.reflect.full.IllegalCallableAccessException", thirdModule, kotlinReflectLibrary)

        // Passing a wrong contextual module
        testClassModule("kotlin.reflect.full.IllegalCallableAccessException", thirdModule, kotlinReflectLibrary, firstStdlibLibrary)
    }

    private fun Library.toKaModule(): KaLibraryModule {
        return toKaLibraryModules(project).single()
    }

    private inline fun <reified T> assertKaModuleType(element: PsiElement, contextualModule: KaModule? = null) {
      com.intellij.testFramework.assertInstanceOf<T>(kaModule(element, contextualModule))
    }

    private fun kaModule(
      element: PsiElement,
      contextualModule: KaModule? = null,
    ): KaModule = KotlinProjectStructureProvider.Companion.getModule(project, element, useSiteModule = contextualModule)

    private inline fun <reified T : KaModule> kaModuleWithAssertion(
      element: PsiElement,
      contextualModule: KaModule? = null
    ): T = KotlinProjectStructureProvider.Companion.getModule(
        project,
        element,
        useSiteModule = contextualModule,
    ).requireIs<T>()

    private inline fun <reified T> assertKaModuleType(fileName: String) {
        val file = getFile(fileName)
        assertKaModuleType<T>(file)
    }

    private fun getFile(
      name: String,
      scope: GlobalSearchScope = GlobalSearchScope.everythingScope(project),
    ): PsiFile = findFile(name, scope) ?: error("File $name is not found")

    private fun findFile(name: String, scope: GlobalSearchScope): PsiFile? {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)
        val processor = object : CommonProcessors.FindProcessor<VirtualFile?>() {
            override fun accept(t: VirtualFile?): Boolean = t?.nameSequence == name
        }

        FileTypeIndex.processFiles(fileType, processor, scope)
        return processor.foundValue?.toPsiFile(project)
    }

    private fun createModule(
      moduleName: String,
      srcContentSpec: DirectoryContentSpec? = null,
      testContentSpec: DirectoryContentSpec? = null,
      resourceContentSpec: DirectoryContentSpec? = null,
    ): Module {
        val module = createModule(moduleName)
        if (srcContentSpec != null) {
            val srcRoot = srcContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addSourceContentToRoots(/* module = */ module, /* vDir = */ srcRoot, /* testSource = */ false)
        }

        if (testContentSpec != null) {
            val testRoot = testContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addSourceContentToRoots(/* module = */ module, /* vDir = */ testRoot, /* testSource = */ true)
        }

        if (resourceContentSpec != null) {
            val resourceRoot = resourceContentSpec.generateInVirtualTempDir()
            PsiTestUtil.addResourceContentToRoots(/* module = */ module, /* vDir = */ resourceRoot, /* testResource = */ false)
        }

        return module
    }
}