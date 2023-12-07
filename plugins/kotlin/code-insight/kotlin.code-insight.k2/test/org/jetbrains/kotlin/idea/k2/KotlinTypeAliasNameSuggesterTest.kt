// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Case
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.psi.KtFunction

class KotlinTypeAliasNameSuggesterTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

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
                with(KotlinNameSuggester(Case.PASCAL)) {
                    val actualName = suggestTypeAliasName(targetTypeElement)
                    assertEquals(expectedName, actualName)
                }
            }
        }
    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
    override fun getTestDataPath() = KotlinRoot.PATH.toString()
}