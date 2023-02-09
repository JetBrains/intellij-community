// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SortModifiersInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/cleanup")
@RunWith(JUnit38ClassRunner::class)
class KotlinCleanupInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    private fun doTest(dir: String, result: String, vararg files: String) {
        myFixture.enableInspections(KotlinCleanupInspection::class.java)
        myFixture.enableInspections(SortModifiersInspection::class.java)
        myFixture.enableInspections(RedundantModalityModifierInspection::class.java)
        myFixture.configureByFiles(*files.map { "$dir/$it" }.toTypedArray())

        val project = myFixture.project
        val managerEx = InspectionManager.getInstance(project)
        val globalContext = managerEx.createNewGlobalContext() as GlobalInspectionContextBase
        val analysisScope = AnalysisScope(myFixture.file)
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        globalContext.codeCleanup(analysisScope, profile, "Cleanup", null, true)

        myFixture.checkResultByFile("$dir/$result")
    }

    fun testBasic() {
        doTest("basic", "basic.kt.after", "basic.kt", "JavaAnn.java", "deprecatedSymbols.kt")
    }

    fun testExpressionWithMultipleDeprecatedCalls() {
        doTest(
            "expressionWithMultipleDeprecatedCalls",
            "expressionWithMultipleDeprecatedCalls.kt.after",
            "expressionWithMultipleDeprecatedCalls.kt"
        )
    }

    fun testFileWithAnnotationToSuppressDeprecation() {
        doTest(
            "fileWithAnnotationToSuppressDeprecation",
            "fileWithAnnotationToSuppressDeprecation.kt.after",
            "fileWithAnnotationToSuppressDeprecation.kt",
            "deprecatedSymbols.kt"
        )
    }
}
