// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hint.ShowParameterInfoContext
import com.intellij.codeInsight.hint.ShowParameterInfoHandler
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.util.registryFlag
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Paths

abstract class AbstractParameterInfoTest : KotlinLightCodeInsightFixtureTestCase() {
    private var mockLibraryFacility: MockLibraryFacility? = null
    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    protected var isMultiline by registryFlag("kotlin.multiline.function.parameters.info")

    override fun setUp() {
        super.setUp()

        val root = KotlinTestUtils.getTestsRoot(this::class.java)
        if (root.contains("Lib")) {
            mockLibraryFacility = MockLibraryFacility(source = File("$root/sharedLib"))
            mockLibraryFacility?.setUp(module)
        }

        myFixture.testDataPath = IDEA_TEST_DATA_DIR.resolve("parameterInfo").slashedPath
    }

    override fun tearDown() = runAll(
        ThrowableRunnable { mockLibraryFacility?.tearDown(module) },
        ThrowableRunnable { super.tearDown() },
    )

    protected fun doTest(fileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(fileName),
            IgnoreTests.DIRECTIVES.of(pluginMode)
        ) {
            doActualTest(fileName)
        }
    }

    private fun doActualTest(fileName: String) {
        val prefix = FileUtil.getNameWithoutExtension(PathUtil.getFileName(fileName))
        val mainFile = File(FileUtil.toSystemDependentName(fileName))
        mainFile.parentFile
            .listFiles { _, name ->
                name.startsWith("$prefix.") &&
                        name != mainFile.name &&
                        name.substringAfterLast(".") in setOf("java", "kt")
            }!!
            .forEach { myFixture.configureByFile(it.canonicalPath) }

        myFixture.configureByFile(File(fileName).canonicalPath)

        val file = myFixture.file

        val originalFileText = file.text
        withCustomCompilerOptions(originalFileText, project, myFixture.module) {

            val context = ShowParameterInfoContext(editor, project, file, editor.caretModel.offset, -1, true)

            lateinit var handler: ParameterInfoHandler<PsiElement, Any>
            lateinit var mockCreateParameterInfoContext: MockCreateParameterInfoContext
            lateinit var parameterOwner: PsiElement
            executeOnPooledThreadInReadAction {
                if (file is KtFile) {
                    val handlers =
                        ShowParameterInfoHandler.getHandlers(project, KotlinLanguage.INSTANCE)
                    @Suppress("UNCHECKED_CAST")
                    handler =
                        handlers.firstOrNull { it.findElementForParameterInfo(context) != null } as? ParameterInfoHandler<PsiElement, Any>
                            ?: error("Could not find parameter info handler")
                } else {
                    val handlers =
                        ShowParameterInfoHandler.getHandlers(project, JavaLanguage.INSTANCE)
                    handler = handlers.firstOrNull { it.findElementForParameterInfo(context) != null } as? ParameterInfoHandler<PsiElement, Any>
                        ?: error("Could not find parameter info handler")
                }

                mockCreateParameterInfoContext = MockCreateParameterInfoContext(file, myFixture)
                parameterOwner = handler.findElementForParameterInfo(mockCreateParameterInfoContext) as PsiElement
            }

            val textToType = InTextDirectivesUtils.findStringWithPrefixes(originalFileText, "// TYPE:")
            if (textToType != null) {
                myFixture.type(textToType)
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }

            lateinit var parameterInfoUIContext: MockParameterInfoUIContext
            executeOnPooledThreadInReadAction {
                //to update current parameter index
                val updateContext = MockUpdateParameterInfoContext(file, myFixture, mockCreateParameterInfoContext)
                val elementForUpdating = handler.findElementForUpdatingParameterInfo(updateContext)
                if (elementForUpdating != null) {
                    handler.updateParameterInfo(elementForUpdating, updateContext)
                }
                parameterInfoUIContext = MockParameterInfoUIContext(parameterOwner, updateContext.currentParameter)
            }


            mockCreateParameterInfoContext.itemsToShow?.forEach {
                handler.updateUI(it, parameterInfoUIContext)
            }


            val actual = parameterInfoUIContext.resultText

            val expectedFile = run {
                val extension = when {
                    isFirPlugin && !isMultiline -> "k2.txt"
                    isFirPlugin && isMultiline -> "k2_multiline.txt"
                    else -> "k1.txt"
                }
                mainFile.toPath().resolveSibling("${mainFile.nameWithoutExtension}.${extension}")
            }

            KotlinTestUtils.assertEqualsToFile(expectedFile, actual)
        }
    }
}
