// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.psi

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class InjectedKotlinHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    // Kotlin disables several diagnostics inside injected fragments, e.g. inside Markdown fence blocks

    private val injectionTestFixture: InjectionTestFixture get() = InjectionTestFixture(myFixture)
    private val tripleQuote = "\"\"\""

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithScriptRuntime()

    fun testNoUnresolvedCode() {
        doTest(
            "Unresolved.kt", """
                // language=kotlin
                val x = $tripleQuote
                    val data = application.run()
                    <caret>
                    class Demo: Parent { }  

                    val x: Int = "error"
                $tripleQuote
            """.trimIndent()
        )
    }

    fun testNoOverrideErrors() {
        doTest(
            "Overrides.kt", """
                // language=kotlin
                val x = $tripleQuote
                    class Demo: Parent {
                        override val data: Int = 100
                        <caret>
                    }  
                $tripleQuote
            """.trimIndent()
        )
    }

    private fun doTest(fileName: String, text: String) {
        myFixture.configureByText(fileName, text)

        dispatchAllInvocationEventsInIdeEventQueue()
        waitForAsyncTaskCompletion()

        injectionTestFixture.assertInjectedLangAtCaret("kotlin")

        myFixture.checkHighlighting()
    }
}