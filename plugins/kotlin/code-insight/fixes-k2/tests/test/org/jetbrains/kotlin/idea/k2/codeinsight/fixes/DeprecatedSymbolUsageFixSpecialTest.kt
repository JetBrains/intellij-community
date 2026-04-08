// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

@TestRoot("idea/tests")
@TestDataPath("/")
@TestMetadata("testData/quickfix.special/deprecatedSymbolUsage")
class DeprecatedSymbolUsageFixSpecialTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    fun testMemberInCompiledClass() {
        doTest("this.matches(input)")
    }

    fun testDefaultParameterValuesFromLibrary() {
        doTest("""prefix + joinTo(StringBuilder(), separator, "", postfix, limit, truncated, transform)""")
    }

    private fun doTest(pattern: String) {
        val testPath = getTestName(true) + ".kt"
        myFixture.configureByFile(testPath)

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        val nameExpression = element!!.parents.firstIsInstance<KtSimpleNameExpression>()
        val fix = ActionUtil.underModalProgress(project, "") {
            DeprecatedSymbolUsageFix(nameExpression,
                ReplaceWithData(pattern, emptyList(), false))
        }

        WriteCommandAction.runWriteCommandAction(project) {
            fix.invoke(project, editor, file)
        }

        myFixture.checkResultByFile("$testPath.after")
    }
}
