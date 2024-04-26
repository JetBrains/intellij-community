// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.findMainOwner
import org.jetbrains.kotlin.idea.base.projectStructure.withLanguageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.checkers.languageVersionSettingsFromText
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.withCustomLanguageAndApiVersion
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractRunConfigurationWithResolveTest : AbstractRunConfigurationBaseTest() {
    fun testMainInTest() {
        configureProject()
        val configuredModule = defaultConfiguredModule

        val languageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion
        withCustomLanguageAndApiVersion(project, module, languageVersion, apiVersion = null) {
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
                    val foundMainCandidates = functionCandidates?.isNotEmpty() ?: false
                    TestCase.assertTrue(
                        "function candidates expected to be found for $file",
                        foundMainCandidates
                    )

                    val foundMainFileContainer = KotlinMainFunctionDetector.getInstance().findMainOwner(ktFile)

                    if (functionCandidates?.any { it.isMainFunction(languageVersionSettings) } == true) {
                        assertNotNull(
                            "$file: Kotlin configuration producer should produce configuration for $file",
                            foundMainFileContainer
                        )
                    } else {
                        assertNull(
                            "$file: Kotlin configuration producer shouldn't produce configuration for $file",
                            foundMainFileContainer
                        )
                    }

                }
            }
        }
    }


    private val detectorConfiguration = KotlinMainFunctionDetector.Configuration()

    private fun KtNamedFunction.isMainFunction(languageSettings: LanguageVersionSettings) =
        KotlinMainFunctionDetector.getInstance().isMain(this, detectorConfiguration)

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

            val isMainFunction = function.isMainFunction(fileLanguageSettings)
            val functionCandidatesAreMain = functionCandidates.map { it.isMainFunction(fileLanguageSettings) }
            val anyFunctionCandidatesAreMain = functionCandidatesAreMain.any { it }
            val allFunctionCandidatesAreNotMain = functionCandidatesAreMain.none { it }

            val text = function.containingFile.text

            val module = file.module!!
            val containingClass = function.toLightMethods().first().containingClass
            val kotlinOrigin = (containingClass as? KtUltraLightClass)?.kotlinOrigin
            val mainClassName = if (kotlinOrigin is KtObjectDeclaration && kotlinOrigin.isCompanion()) {
                // Run configuration for companion object is created for the owner class
                // i.e. `foo.Bar` instead of `foo.Bar.Companion`
                containingClass.parent as PsiClass
            } else {
                containingClass
            }?.qualifiedName!!
            val findMainClassFileSlowResolve = if (text.contains("NO-DUMB-MODE")) {
                findMainClassFile(module, mainClassName, true)
            } else {
                val findMainClassFileResult = AtomicReference<KtFile>()
                DumbModeTestUtils.runInDumbModeSynchronously(project) {
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

            val foundMainContainer = KotlinMainFunctionDetector.getInstance().findMainOwner(function)

            if (isMainFunction) {
                createConfigurationFromMain(project, function.fqName?.asString()!!).checkConfiguration()

                assertNotNull(
                    "$file: Kotlin configuration producer should produce configuration for ${function.fqName?.asString()}",
                    foundMainContainer
                )
            } else {
                try {
                    createConfigurationFromMain(project, function.fqName?.asString()!!).checkConfiguration()
                    fail(
                        "$file: configuration for function ${function.fqName?.asString()} at least shouldn't pass checkConfiguration()",
                    )
                } catch (expected: Throwable) {
                    // ignore
                }

                if (text.startsWith("// entryPointExists")) {
                    assertNotNull(
                        "$file: Kotlin configuration producer should produce configuration for ${function.fqName?.asString()}",
                        foundMainContainer,
                    )
                } else {
                    assertNull(
                        "Kotlin configuration producer shouldn't produce configuration for ${function.fqName?.asString()}",
                        foundMainContainer,
                    )
                }
            }
        }
        return functionCandidates
    }

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("run")
}