// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modules

import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScope
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.requireIs
import com.intellij.util.CommonProcessors.FindProcessor
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModuleOfType
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.moduleLibrary
import org.jetbrains.kotlin.test.util.projectLibrary
import org.jetbrains.kotlin.test.util.addDependency
import org.junit.Assert.assertNotEquals
import java.io.File

class KotlinProjectStructureTest : AbstractMultiModuleTest() {
    override fun getTestProjectJdk(): Sdk = IdeaTestUtil.getMockJdk11()

    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File = throw UnsupportedOperationException()

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
        val sourceModule = ktModuleWithAssertion<KtSourceModule>(sourceFile)

        val libraryClassFile = getFile("KotlinCompileDaemon.class")
        val libraryModuleWithoutContext = ktModuleWithAssertion<KtLibraryModule>(libraryClassFile)
        assertEquals(libraryName, libraryModuleWithoutContext.libraryName)

        val libraryModuleWithUnrelatedContext = ktModuleWithAssertion<KtLibraryModule>(libraryClassFile, sourceModule)
        assertEquals(libraryModuleWithoutContext, libraryModuleWithUnrelatedContext)

        val librarySourceFile = getFile("_Collections.kt")
        val librarySourceModuleWithoutContext = ktModuleWithAssertion<KtLibrarySourceModule>(librarySourceFile)
        assertEquals(libraryModuleWithoutContext.librarySources, librarySourceModuleWithoutContext)

        val librarySourceModuleWithUnrelatedContext = ktModuleWithAssertion<KtLibrarySourceModule>(librarySourceFile, sourceModule)
        assertEquals(librarySourceModuleWithoutContext, librarySourceModuleWithUnrelatedContext)
    }

    fun `test deduplicate libraries`() {
        val sharedLibraryContentRoot = TestKotlinArtifacts.kotlinDaemon.jarRoot
        val moduleA = createModule(
            moduleName = "a",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("A.kt", "class A")
                }
            }
        )

        moduleLibrary(moduleA, libraryName = null, classesRoot = sharedLibraryContentRoot)

        val moduleB = createModule(
            moduleName = "b",
            srcContentSpec = directoryContent {
                dir("one") {
                    file("B.kt", "class B")
                }
            }
        )

        moduleLibrary(moduleB, libraryName = "library", classesRoot = sharedLibraryContentRoot)

        val sourceAFile = getFile("A.kt")
        val sourceAModule = ktModuleWithAssertion<KtSourceModule>(sourceAFile)

        val sourceBFile = getFile("B.kt")
        val sourceBModule = ktModuleWithAssertion<KtSourceModule>(sourceBFile)

        val libraryFile = getFile("KotlinCompileDaemon.class")
        val libraryAModule = ktModuleWithAssertion<KtLibraryModule>(libraryFile, sourceAModule)
        val libraryBModule = ktModuleWithAssertion<KtLibraryModule>(libraryFile, sourceBModule)
        assertEquals(libraryAModule, libraryBModule)
        assertTrue(libraryAModule in sourceAModule.directRegularDependencies)
        assertTrue(libraryBModule in sourceBModule.directRegularDependencies)
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

        val sourceFile = getFile("Main.kt")

        val libraryScope = LibraryScope(project, library)
        val libraryFile = getFile("KotlinCompileDaemon.class", libraryScope)

        val libraryModuleWithoutContext = ktModuleWithAssertion<KtLibraryModule>(libraryFile)
        assertEquals(libraryName, libraryModuleWithoutContext.libraryName)

        val sourceModule = ktModuleWithAssertion<KtSourceModule>(sourceFile)
        val libraryModuleWithContext = ktModuleWithAssertion<KtLibraryModule>(libraryFile, contextualModule = sourceModule)

        assertEquals(libraryModuleWithoutContext, libraryModuleWithContext)
        assertTrue("The library module must be in dependencies", libraryModuleWithContext in sourceModule.directRegularDependencies)

        val libSourceFile = getFile("Comparator.kt")
        val libSourceModule = ktModuleWithAssertion<KtLibrarySourceModule>(libSourceFile)
        assertEquals(libraryModuleWithoutContext.librarySources, libSourceModule)
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
        moduleLibrary(
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
        val libraryAModuleWithoutContext = ktModuleWithAssertion<KtLibraryModule>(libraryAFile)
        assertEquals(libraryAName, libraryAModuleWithoutContext.libraryName)

        val sourceAModule = ktModuleWithAssertion<KtSourceModule>(sourceAFile)
        val libraryAModuleWithContext = ktModuleWithAssertion<KtLibraryModule>(libraryAFile, contextualModule = sourceAModule)
        assertEquals(libraryAModuleWithoutContext, libraryAModuleWithContext)
        assertTrue("The library module must be in dependencies", libraryAModuleWithContext in sourceAModule.directRegularDependencies)

        // -------> B module logic (the last one in the order)
        val libraryBModuleWithoutContext = ktModuleWithAssertion<KtLibraryModule>(libraryBFile)
        assertEquals(
            "The library name should be from the first module due to a context absence",
            libraryAName,
            libraryBModuleWithoutContext.libraryName,
        )

        val sourceBModule = ktModuleWithAssertion<KtSourceModule>(sourceBFile)
        val libraryBModuleWithContext = ktModuleWithAssertion<KtLibraryModule>(libraryBFile, contextualModule = sourceBModule)
        assertEquals(
            "The library name must be from the corresponding module if a context passed",
            libraryBName,
            libraryBModuleWithContext.libraryName,
        )

        assertNotEquals(
            "The library module must be from the corresponding module if a context passed",
            libraryBModuleWithoutContext,
            libraryBModuleWithContext,
        )

        assertTrue("The library module must be in dependencies", libraryBModuleWithContext in sourceBModule.directRegularDependencies)

        val libraryCFile = getFile("KProperties.class")
        val libraryCModule = ktModuleWithAssertion<KtLibraryModule>(libraryCFile)
        assertEquals(libraryCName, libraryCModule.libraryName)

        val sharedLibrarySourceFile = getFile("_Collections.kt")
        val sharedLibrarySourceModuleWithoutContext = ktModuleWithAssertion<KtLibrarySourceModule>(sharedLibrarySourceFile)
        assertEquals(
            "The library source should be from the first module due to a context absence",
            libraryCModule.librarySources,
            sharedLibrarySourceModuleWithoutContext,
        )

        val sharedLibrarySourceModuleWithCContext = ktModuleWithAssertion<KtLibrarySourceModule>(sharedLibrarySourceFile, sourceAModule)
        assertEquals(
            "The library source must be the same due to the same context as the first library",
            sharedLibrarySourceModuleWithoutContext,
            sharedLibrarySourceModuleWithCContext,
        )

        val sharedLibrarySourceModuleWithBContext = ktModuleWithAssertion<KtLibrarySourceModule>(sharedLibrarySourceFile, sourceBModule)
        assertNotEquals(
            "The library source module must be from the corresponding module if a context passed",
            sharedLibrarySourceModuleWithoutContext,
            sharedLibrarySourceModuleWithBContext,
        )

        assertEquals(libraryBModuleWithContext.librarySources, sharedLibrarySourceModuleWithBContext)
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

        assertKtModuleType<KtSourceModule>("Main.kt")
        assertKtModuleType<KtSourceModule>("Test.kt")
    }

    fun `test that buildSrc sources belong to KtSourceModule`() {
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

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(scriptFile)

        assertKtModuleType<KtSourceModule>(dependency, ktModule(scriptFile))
    }

    fun `test that script in buildSrc belong to KtScriptModule`() {
        createModule(
            moduleName = "buildSrc",
            srcContentSpec = directoryContent {
                file("build.gradle.kts", "")
            }
        )

        val scriptFile = getFile("build.gradle.kts")
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(scriptFile)
        assertKtModuleType<KtScriptModule>(scriptFile, ktModule(scriptFile))
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

        assertKtModuleType<KtSourceModule>(psiDirectory)
    }

    fun `test out of content module`() {
        val dummyRoot = directoryContent { file("dummy.kt", "class A") }
            .generateInVirtualTempDir()

        val dummyVirtualFile = dummyRoot.findChild("dummy.kt")!!
        val dummyFile = PsiManager.getInstance(project).findFile(dummyVirtualFile)!!

        assertKtModuleType<KtNotUnderContentRootModule>(dummyFile)

        createModule(
            moduleName = "m",
            resourceContentSpec = directoryContent {
                dir("wd") {
                    file("resource.kt", "class B")
                }
            },
        )

        // KTIJ-26841: Should be KtNotUnderContentRootModule as well
        assertKtModuleType<KtSourceModule>("resource.kt")
    }

    fun `test dangling file module`() {
        val file = createDummyFile("dummy.kt", "class A")
        assertKtModuleType<KtDanglingFileModule>(file)
    }

    fun `test dangling script file module`() {
        val file = createDummyFile("dummy.kts", "class A")
        assertKtModuleType<KtDanglingFileModule>(file)
    }

    fun `test script module`() {
        val dummyRoot = directoryContent { file("dummy.kts", "class A") }
            .generateInVirtualTempDir()

        val dummyVirtualFile = dummyRoot.findChild("dummy.kts")!!
        val dummyFile = PsiManager.getInstance(project).findFile(dummyVirtualFile)!!

        // It is KtNotUnderContentRootModule and not KtScriptModule due to a lack of virtual file
        assertKtModuleType<KtScriptModule>(dummyFile)

        createModule(
            moduleName = "m",
            resourceContentSpec = directoryContent {
                dir("wd") {
                    file("myScript.kts", "class B")
                }
            },
        )

        assertKtModuleType<KtScriptModule>("myScript.kts")
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
            val expectedLibraryKtModule = expectedLibrary.toKtModule()
            val expectedLibrarySourceKtModule = expectedLibraryKtModule.librarySources!!

            val contextLibraryKtModule = contextLibrary.toKtModule()
            val contextLibrarySourceKtModule = contextLibraryKtModule.librarySources!!

            val javaLightClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.moduleWithLibrariesScope(module)) as KtLightClass

            val kotlinDecompiledClass = javaLightClass.kotlinOrigin!!
            assert(kotlinDecompiledClass.containingKtFile.isCompiled)

            val actualLibraryKtModule = ProjectStructureProvider.getInstance(project)
                .getModule(kotlinDecompiledClass, contextualModule = contextLibraryKtModule)

            assertEquals(expectedLibraryKtModule, actualLibraryKtModule)

            val kotlinSourceClass = service<KotlinDeclarationNavigationPolicy>().getNavigationElement(kotlinDecompiledClass)
            assertFalse(kotlinSourceClass.containingKtFile.isCompiled)

            val actualLibrarySourceKtModule = ProjectStructureProvider.getInstance(project)
                .getModule(kotlinSourceClass, contextualModule = contextLibrarySourceKtModule)

            assertEquals(expectedLibrarySourceKtModule, actualLibrarySourceKtModule)
        }

        testClassModule("kotlin.KotlinVersion", firstModule, firstStdlibLibrary)
        testClassModule("kotlin.KotlinVersion", secondModule, secondStdlibLibrary)
        testClassModule("kotlin.reflect.full.IllegalCallableAccessException", thirdModule, kotlinReflectLibrary)

        // Passing a wrong contextual module
        testClassModule("kotlin.reflect.full.IllegalCallableAccessException", thirdModule, kotlinReflectLibrary, firstStdlibLibrary)
    }

    private fun Library.toKtModule(): KtLibraryModule {
        val expectedLibraryInfo = LibraryInfoCache.getInstance(project)[this].single()
        return expectedLibraryInfo.toKtModuleOfType<KtLibraryModule>()
    }

    private inline fun <reified T> assertKtModuleType(element: PsiElement, contextualModule: KtModule? = null) {
        assertInstanceOf<T>(ktModule(element, contextualModule))
    }

    private fun ktModule(
        element: PsiElement,
        contextualModule: KtModule? = null,
    ): KtModule = ProjectStructureProvider.getModule(project, element, contextualModule = contextualModule)

    private inline fun <reified T : KtModule> ktModuleWithAssertion(
        element: PsiElement,
        contextualModule: KtModule? = null
    ): T = ProjectStructureProvider.getModule(
        project,
        element,
        contextualModule = contextualModule,
    ).requireIs<T>()

    private inline fun <reified T> assertKtModuleType(fileName: String) {
        val file = getFile(fileName)
        assertKtModuleType<T>(file)
    }

    private fun getFile(
        name: String,
        scope: GlobalSearchScope = GlobalSearchScope.everythingScope(project),
    ): PsiFile = findFile(name, scope) ?: error("File $name is not found")

    private fun findFile(name: String, scope: GlobalSearchScope): PsiFile? {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)
        val processor = object : FindProcessor<VirtualFile?>() {
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
