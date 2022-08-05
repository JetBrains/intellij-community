// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.test

import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFunction

class KotlinTypeAliasNameSuggesterTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testSimple() = test("String", "StringAlias")
    fun testNullable() = test("String?", "NullableString")
    fun testFullyQualified() = test("java.lang.String", "StringAlias")
    fun testStringList() = test("List<String>", "ListOfString")
    fun testFunctionType() = test("(String) -> Int", "StringToInt")
    fun testComplexFunctionType() = test("(String, List<Int>?) -> Boolean", "StringNullableListOfIntPredicate")

    private fun test(typeText: String, expectedName: String) {
        val fileText = "fun test<caret>(param: $typeText) {}"
        val file = myFixture.configureByText("file.kt", fileText)

        val targetDeclaration = file.findElementAt(myFixture.caretOffset)!!.findParentOfType<KtFunction>()!!
        val targetTypeElement = targetDeclaration.valueParameters.single().typeReference!!.typeElement!!

        executeOnPooledThreadInReadAction {
            analyze(targetDeclaration) {
                val nameSuggester = KotlinNameSuggester(KotlinNameSuggester.Case.PASCAL)
                val actualName = nameSuggester.suggestTypeAliasName(targetTypeElement)
                assertEquals(expectedName, actualName)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }
}