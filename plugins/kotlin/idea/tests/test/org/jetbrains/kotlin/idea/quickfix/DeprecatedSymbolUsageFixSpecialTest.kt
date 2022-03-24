// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWith
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.test.TestRoot
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData/quickfix.special/deprecatedSymbolUsage")
class DeprecatedSymbolUsageFixSpecialTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

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
            DeprecatedSymbolUsageFix(nameExpression, ReplaceWith(pattern, emptyList(), false))
        }

        project.executeWriteCommand("") {
            fix.invoke(project, editor, file)
        }

        myFixture.checkResultByFile("$testPath.after")
    }
}
