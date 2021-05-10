// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringFactory
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.MapDataContext
import junit.framework.TestCase
import org.jdom.Element
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.checkers.languageVersionSettingsFromText
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.project.withLanguageVersionSettings
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration.Companion.findMainClassFile
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.withCustomLanguageAndApiVersion
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

private const val RUN_PREFIX = "// RUN:"

@RunWith(JUnit38ClassRunner::class)
class RunConfigurationTest : AbstractRunConfigurationTest() {
    fun testMainInTest() {
        configureProject()
        val configuredModule = defaultConfiguredModule

        val languageVersion = LanguageVersionSettingsImpl.DEFAULT.languageVersion
        withCustomLanguageAndApiVersion(project, module, languageVersion.versionString, apiVersion = null) {
            val runConfiguration = createConfigurationFromMain(project, "some.main")
            val javaParameters = getJavaRunParameters(runConfiguration)

            assertTrue(javaParameters.classPath.rootDirs.contains(configuredModule.srcOutputDir))
            assertTrue(javaParameters.classPath.rootDirs.contains(configuredModule.testOutputDir))

            fun VirtualFile.findKtFiles(): List<VirtualFile> {
                return children.filter { it.isDirectory }.flatMap { it.findKtFiles() } + children.filter { it.extension == "kt" }
            }

            val files = configuredModule.srcDir?.findKtFiles().orEmpty()
            val psiManager = PsiManager.getInstance(project)

            for (file in files) {
                val ktFile = psiManager.findFile(file) as? KtFile ?: continue
                val languageVersionSettings = languageVersionSettingsFromText(listOf(ktFile.text))

                module.withLanguageVersionSettings(languageVersionSettings) {
                    var functionCandidates: List<KtNamedFunction>? = null
                    ktFile.acceptChildren(
                        object : KtTreeVisitorVoid() {
                            override fun visitNamedFunction(function: KtNamedFunction) {
                                functionCandidates = functionVisitor(languageVersionSettings, function)
                            }
                        }
                    )
                    TestCase.assertTrue(
                        "function candidates expected to be found for $file",
                        functionCandidates?.isNotEmpty() ?: false
                    )
                }
            }
        }
    }

    fun testDependencyModuleClasspath() {
        configureProject()
        val configuredModule = defaultConfiguredModule
        val configuredModuleWithDependency = getConfiguredModule("moduleWithDependency")

        ModuleRootModificationUtil.addDependency(configuredModuleWithDependency.module, configuredModule.module)

        val kotlinRunConfiguration = createConfigurationFromMain(project, "some.test.main")
        kotlinRunConfiguration.setModule(configuredModuleWithDependency.module)

        val javaParameters = getJavaRunParameters(kotlinRunConfiguration)

        assertTrue(javaParameters.classPath.rootDirs.contains(configuredModule.srcOutputDir))
        assertTrue(javaParameters.classPath.rootDirs.contains(configuredModuleWithDependency.srcOutputDir))
    }

    fun testLongCommandLine() {
        configureProject()
        ModuleRootModificationUtil.addDependency(module, createLibraryWithLongPaths(project))

        val kotlinRunConfiguration = createConfigurationFromMain(project, "some.test.main")
        kotlinRunConfiguration.setModule(module)

        val javaParameters = getJavaRunParameters(kotlinRunConfiguration)
        val commandLine = javaParameters.toCommandLine().commandLineString
        assert(commandLine.length > javaParameters.classPath.pathList.joinToString(File.pathSeparator).length) {
            "Wrong command line length: \ncommand line = $commandLine, \nclasspath = ${javaParameters.classPath.pathList.joinToString()}"
        }
    }

    fun testClassesAndObjects() = checkClasses()

    fun testInJsModule() = checkClasses(Platform.JavaScript)

    fun testRedirectInputPath() {
        configureProject()

        val runConfiguration1 = createConfigurationFromMain(project, "some.main")
        runConfiguration1.inputRedirectOptions.apply {
            isRedirectInput = true
            redirectInputPath = "someFile"
        }

        val elementWrite = Element("temp")
        runConfiguration1.writeExternal(elementWrite)

        val runConfiguration2 = createConfigurationFromMain(project, "some.main")
        runConfiguration2.readExternal(elementWrite)

        assertEquals(runConfiguration1.inputRedirectOptions.isRedirectInput, runConfiguration2.inputRedirectOptions.isRedirectInput)
        assertEquals(runConfiguration1.inputRedirectOptions.redirectInputPath, runConfiguration2.inputRedirectOptions.redirectInputPath)
    }

    fun testIsEditableInADumbMode() {
        configureProject()

        val runConfiguration = createConfigurationFromObject("foo.Bar")

        with(runConfiguration.factory!!) {
            assertTrue(isEditableInDumbMode)
            assertTrue(safeAs<PossiblyDumbAware>()!!.isDumbAware)
        }
    }

    fun testUpdateOnClassRename() {
        configureProject()

        val runConfiguration = createConfigurationFromObject("renameTest.Foo")

        val obj = KotlinFullClassNameIndex.getInstance().get("renameTest.Foo", project, project.allScope()).single()
        val rename = RefactoringFactory.getInstance(project).createRename(obj, "Bar")
        rename.run()

        assertEquals("renameTest.Bar", runConfiguration.runClass)
    }

    fun testUpdateOnPackageRename() {
        configureProject()

        val runConfiguration = createConfigurationFromObject("renameTest.Foo")

        val pkg = JavaPsiFacade.getInstance(project).findPackage("renameTest") ?: error("Package 'renameTest' not found")
        val rename = RefactoringFactory.getInstance(project).createRename(pkg, "afterRenameTest")
        rename.run()

        assertEquals("afterRenameTest.Foo", runConfiguration.runClass)
    }

    fun testWithModuleForJdk6() {
        checkModuleInfoName(null, Platform.Jvm(IdeaTestUtil.getMockJdk16()))
    }

    fun testWithModuleForJdk9() {
        checkModuleInfoName("MAIN", Platform.Jvm(IdeaTestUtil.getMockJdk9()))
    }

    fun testWithModuleForJdk9WithoutModuleInfo() {
        checkModuleInfoName(null, Platform.Jvm(IdeaTestUtil.getMockJdk9()))
    }

    private fun checkModuleInfoName(moduleName: String?, platform: Platform) {
        configureProject(platform)

        val javaParameters = getJavaRunParameters(createConfigurationFromMain(project, "some.main"))
        assertEquals(moduleName, javaParameters.moduleName)
    }

    private fun checkClasses(platform: Platform = Platform.Jvm()) {
        configureProject(platform)
        val srcDir = defaultConfiguredModule.srcDir ?: error("Module doesn't have a production source set")

        val expectedClasses = ArrayList<String>()
        val actualClasses = ArrayList<String>()

        val fileName = "test.kt"
        val testKtVirtualFile = srcDir.findFileByRelativePath(fileName) ?: error("Can't find VirtualFile for $fileName")
        val testFile = PsiManager.getInstance(project).findFile(testKtVirtualFile) ?: error("Can't find PSI for $fileName")

        val visitor = object : KtTreeVisitorVoid() {
            override fun visitComment(comment: PsiComment) {
                val declaration = comment.getStrictParentOfType<KtNamedDeclaration>()!!
                val text = comment.text ?: return
                if (!text.startsWith(RUN_PREFIX)) return

                val expectedClass = text.substring(RUN_PREFIX.length).trim()
                if (expectedClass.isNotEmpty()) expectedClasses.add(expectedClass)

                val dataContext = MapDataContext()
                dataContext.put(Location.DATA_KEY, PsiLocation(project, declaration))
                val context = ConfigurationContext.getFromContext(dataContext)
                val actualClass = (context.configuration?.configuration as? KotlinRunConfiguration)?.runClass
                if (actualClass != null) {
                    actualClasses.add(actualClass)
                }
            }
        }

        testFile.accept(visitor)
        assertEquals(expectedClasses, actualClasses)
    }

    private fun createConfigurationFromObject(@Suppress("SameParameterValue") objectFqn: String): KotlinRunConfiguration {
        val obj = KotlinFullClassNameIndex.getInstance().get(objectFqn, project, project.allScope()).single()
        val mainFunction = obj.declarations.single { it is KtFunction && it.getName() == "main" }
        return createConfigurationFromElement(mainFunction, true) as KotlinRunConfiguration
    }

    companion object {
        private fun functionVisitor(fileLanguageSettings: LanguageVersionSettings, function: KtNamedFunction): List<KtNamedFunction> {
            val project = function.project
            val file = function.containingKtFile
            val options = function.bodyExpression?.allChildren?.filterIsInstance<PsiComment>()
                ?.map { it.text.trim().replace("//", "").trim() }
                ?.filter { it.isNotBlank() }?.toList() ?: emptyList()
            val functionCandidates = file.collectDescendantsOfType<PsiComment>()
                .filter {
                    val option = it.text.trim().replace("//", "").trim()
                    "yes" == option || "no" == option
                }
                .mapNotNull { it.getParentOfType<KtNamedFunction>(true) }

            if (options.isNotEmpty()) {
                val assertIsMain = "yes" in options
                val assertIsNotMain = "no" in options

                fun isMainFunction(f: KtNamedFunction) =
                    MainFunctionDetector(fileLanguageSettings) { it.resolveToDescriptorIfAny() }.isMain(f)

                val isMainFunction = isMainFunction(function)
                val functionCandidatesAreMain = functionCandidates.map(::isMainFunction)
                val anyFunctionCandidatesAreMain = functionCandidatesAreMain.any { it }
                val allFunctionCandidatesAreNotMain = functionCandidatesAreMain.none { it }

                val text = function.containingFile.text

                val module = file.module!!
                val mainClassName = function.toLightMethods().first().containingClass?.qualifiedName!!
                val findMainClassFileSlowResolve = if (text.contains("NO-DUMB-MODE")) {
                    findMainClassFile(module, mainClassName, true)
                } else {
                    val findMainClassFileResult = AtomicReference<KtFile>()
                    DumbServiceImpl.getInstance(project).runInDumbMode {
                        findMainClassFileResult.set(findMainClassFile(module, mainClassName, true))
                    }
                    findMainClassFileResult.get()
                }

                val findMainClassFile = findMainClassFile(module, mainClassName, false)
                TestCase.assertEquals(
                    "findMainClassFile $mainClassName in useSlowResolve $findMainClassFileSlowResolve mode diff from normal mode $findMainClassFile",
                    findMainClassFileSlowResolve,
                    findMainClassFile
                )

                if (assertIsMain) {
                    assertTrue("$file: The function ${function.fqName?.asString()} should be main", isMainFunction)
                    if (anyFunctionCandidatesAreMain) {
                        assertEquals("$file: The function ${function.fqName?.asString()} is main", file, findMainClassFile)
                    }
                }
                if (assertIsNotMain) {
                    assertFalse("$file: The function ${function.fqName?.asString()} should NOT be main", isMainFunction)
                    if (allFunctionCandidatesAreNotMain) {
                        assertNull("$file / $findMainClassFile: The function ${function.fqName?.asString()} is NOT main", findMainClassFile)
                    }
                }

                if (isMainFunction) {
                    createConfigurationFromMain(project, function.fqName?.asString()!!).checkConfiguration()

                    assertNotNull(
                        "$file: Kotlin configuration producer should produce configuration for ${function.fqName?.asString()}",
                        KotlinRunConfigurationProducer.getEntryPointContainer(function),
                    )
                } else {
                    try {
                        createConfigurationFromMain(project, function.fqName?.asString()!!).checkConfiguration()
                        fail(
                            "$file: configuration for function ${function.fqName?.asString()} at least shouldn't pass checkConfiguration()",
                        )
                    } catch (expected: Throwable) {
                    }

                    if (text.startsWith("// entryPointExists")) {
                        assertNotNull(
                            "$file: Kotlin configuration producer should produce configuration for ${function.fqName?.asString()}",
                            KotlinRunConfigurationProducer.getEntryPointContainer(function),
                        )
                    } else {
                        assertNull(
                            "Kotlin configuration producer shouldn't produce configuration for ${function.fqName?.asString()}",
                            KotlinRunConfigurationProducer.getEntryPointContainer(function),
                        )
                    }
                }
            }
            return functionCandidates
        }

        private fun createConfigurationFromMain(project: Project, mainFqn: String): KotlinRunConfiguration {
            val scope = project.allScope()
            val mainFunction =
                KotlinTopLevelFunctionFqnNameIndex.getInstance().get(mainFqn, project, scope).firstOrNull()
                    ?: run {
                        val className = StringUtil.getPackageName(mainFqn)
                        val shortName = StringUtil.getShortName(mainFqn)
                        KotlinFullClassNameIndex.getInstance().get(className, project, scope)
                            .flatMap { it.declarations }
                            .filterIsInstance<KtNamedFunction>()
                            .firstOrNull { it.name == shortName }
                    } ?: error("unable to look up top level function $mainFqn")
            return createConfigurationFromElement(mainFunction) as KotlinRunConfiguration
        }
    }

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("run")
}
