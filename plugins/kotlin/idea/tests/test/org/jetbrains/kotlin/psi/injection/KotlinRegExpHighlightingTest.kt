// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.psi.injection

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * @author Bas Leijdekkers
 */
class KotlinRegExpHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testUnescapedDollarSign() = doTest("\\\\\$<error descr=\"[UNRESOLVED_REFERENCE] Unresolved reference: A\">A</error>")

    private fun doTest(@NonNls code: String) {
        // we need this allow call here because KotlinLanguageInjectionContributor does some resolve that loads the AST
        // of kotlin/text/StringsKt.class,
        myFixture.allowTreeAccessForAllFiles()

        myFixture.configureByText("Test.kt", "fun x() = \"$code\".toRegex()")
        myFixture.testHighlighting()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }
}