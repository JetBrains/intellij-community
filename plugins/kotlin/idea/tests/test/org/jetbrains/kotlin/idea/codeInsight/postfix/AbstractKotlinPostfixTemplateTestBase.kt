// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.let
import kotlin.text.replace

abstract class AbstractKotlinPostfixTemplateTestBase : NewLightKotlinCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJvmLightProjectDescriptor.DEFAULT
    }

    protected fun performTest() {
        val disableDirective = when (pluginKind) {
            KotlinPluginKind.FE10_PLUGIN -> IgnoreTests.DIRECTIVES.IGNORE_K1
            KotlinPluginKind.FIR_PLUGIN -> IgnoreTests.DIRECTIVES.IGNORE_K2
        }
        IgnoreTests.runTestIfNotDisabledByFileDirective(testRootPath.resolve(testMethodPath), disableDirective, "after") {
            myFixture.configureByDefaultFile()
            templateName?.let { myFixture.type(".$it") }

            val fileText = file.text
            val template = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $TEMPLATE:")

            if (template != null) {
                myFixture.type(template.replace("\\t", "\t"))
            } else {
                myFixture.type("\t")
            }

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
        val templateState = TemplateManagerImpl.getTemplateState(editor)
        if (templateState?.isFinished() == false) {
            project.executeCommand("") { templateState.gotoEnd(false) }
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
        const val ALLOW_MULTIPLE_EXPRESSIONS = "ALLOW_MULTIPLE_EXPRESSIONS"
        const val TEMPLATE = "TEMPLATE"
    }
}