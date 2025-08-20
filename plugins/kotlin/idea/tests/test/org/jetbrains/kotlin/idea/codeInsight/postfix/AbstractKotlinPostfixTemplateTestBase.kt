// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.util.isInDumbMode
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.io.path.relativeTo

abstract class AbstractKotlinPostfixTemplateTestBase : NewLightKotlinCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJvmLightProjectDescriptor.DEFAULT
    }

    protected fun performTest() {
        val disableDirective = IgnoreTests.DIRECTIVES.of(pluginMode)
        myFixture.configureByDefaultFile()
        templateName?.let { myFixture.type(".$it") }

        val fileText = file.text
        val template = InTextDirectivesUtils.findStringWithPrefixes(fileText, TEMPLATE_DIRECTIVE)

        val templateKey = templateName ?: run {
            val text = this.editor.getDocument().getText(TextRange(0, this.editor.getCaretModel().offset))
            text.substringAfterLast(".")
        }
        val projectInDumbMode = project.isInDumbMode
        val postfixTemplate: PostfixTemplate? =
            LanguagePostfixTemplate.LANG_EP.forLanguage(KotlinLanguage.INSTANCE)
                .templates.firstOrNull { it.key == ".$templateKey" }
        val postfixTemplateDumbAware = DumbService.isDumbAware(postfixTemplate)
        try {
            IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), disableDirective, "after") {
                check(postfixTemplate != null) { "Unable to find PostfixTemplate for `$templateKey`" }

                KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable) { options -> options.last() }

                if (template != null) {
                    myFixture.type(template.replace("\\t", "\t"))
                } else {
                    myFixture.type("\t")
                }
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                PlatformTestUtil.waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)

                val allowMultipleExpressions = InTextDirectivesUtils.isDirectiveDefined(fileText, ALLOW_MULTIPLE_EXPRESSIONS)
                val suggestedExpressions = with(KotlinPostfixTemplateInfo) { file.suggestedExpressions }

                if (suggestedExpressions.size > 1) {
                    assertTrue("Only one expression should be suggested, but $suggestedExpressions were found", allowMultipleExpressions)
                } else {
                    assertFalse(
                        "$ALLOW_MULTIPLE_EXPRESSIONS is declared in file, but $suggestedExpressions were found",
                        allowMultipleExpressions,
                    )
                }

                myFixture.checkContentByExpectedPath(".after", addSuffixAfterExtension = isOldTestData)
            }
        } catch (e: Throwable) {
            // ignore failed test when postfixTemplate is not dumbAware and project in dumbMode
            if (!(projectInDumbMode && !postfixTemplateDumbAware)) {
                throw e
            } else {
                LOG.info("$name is ignored as $postfixTemplate is not dumbAware while project is in dumbMode")
            }
        } finally {
            val templateState = TemplateManagerImpl.getTemplateState(editor)
            if (templateState?.isFinished() == false) {
                project.executeCommand("") { templateState.gotoEnd(false) }
            }
        }
    }

    private val templateName: String?
        get() = if (!isOldTestData) Paths.get(testDataPath).name else null

    private val isOldTestData: Boolean
        get() = Paths.get(testDataPath)
            .relativeTo(KotlinTestHelpers.getTestRootPath(javaClass))
            .toString()
            .contains("oldTestData")

    companion object {
        const val ALLOW_MULTIPLE_EXPRESSIONS: String = "ALLOW_MULTIPLE_EXPRESSIONS"
        const val TEMPLATE_DIRECTIVE: String = "TEMPLATE:"
    }
}