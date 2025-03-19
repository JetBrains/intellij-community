// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.psi

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
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
        val file = myFixture.configureByText(fileName, text)

        // this is required to configure Script roots out of DaemonCodeAnalyzer run,
        // otherwise it throws errors from EDT.dispatchAllInvocationEvents():
        // > PSI/document/model changes are not allowed during highlighting
        DefaultScriptingSupport.getInstance(myFixture.project)
            .getOrLoadConfiguration(file.virtualFile, null)

        dispatchAllInvocationEventsInIdeEventQueue()
        waitForAsyncTaskCompletion()

        injectionTestFixture.assertInjectedLangAtCaret("kotlin")

        myFixture.checkHighlighting()
    }
}