// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionSettingsStateComponent
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedClassNames
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinScriptTemplateNotRegisteredInspectionTest : NewLightKotlinCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String = ""

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor(
        listOf(TestKotlinArtifacts.kotlinStdlib, TestKotlinArtifacts.kotlinScriptingCommon),
        emptyList(),
    )

    override fun setUp() {
        super.setUp()
        ScriptDefinitionSettingsStateComponent.getInstance(project).update {
            ScriptDefinitionSettingsStateComponent.State()
        }
        myFixture.enableInspections(KotlinScriptTemplateNotRegisteredInspection())
    }

    fun testNoHighlightOnClassWithoutAnnotation() {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
                package com.example
                class PlainClass
            """.trimIndent()
        )
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.WARNING))
    }

    fun testHighlightsBothIssuesAndOffersBothFixes() {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
                package com.example
                import kotlin.script.experimental.annotations.KotlinScript
                @KotlinScript
                class <warning descr="Script definition is not registered in settings and has no marker file for JAR auto-discovery.">My<caret>Script</warning>
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false, false)
        val fixNames = inspectionFixNames()
        assertContainsElements(fixNames, "Register in Kotlin Scripting settings", "Create marker file")
    }

    fun testOnlyMarkerFixOfferedWhenAlreadyRegistered() {
        ScriptDefinitionSettingsStateComponent.getInstance(project).update {
            it.copy(explicitTemplateClassNames = "com.example.MyScript")
        }
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
                package com.example
                import kotlin.script.experimental.annotations.KotlinScript
                @KotlinScript
                class <warning descr="Script definition has no marker file at META-INF/kotlin/script/templates/.">My<caret>Script</warning>
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false, false)
        val fixNames = inspectionFixNames()
        assertContainsElements(fixNames, "Create marker file")
        assertDoesntContain(fixNames, "Register in Kotlin Scripting settings")
    }

    fun testRegisterQuickFixPopulatesSettings() {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
                package com.example
                import kotlin.script.experimental.annotations.KotlinScript
                @KotlinScript
                class My<caret>Script
            """.trimIndent()
        )
        val fix = myFixture.findSingleIntention("Register in Kotlin Scripting settings")
        myFixture.launchAction(fix)

        val state = ScriptDefinitionSettingsStateComponent.getInstance(project).state
        assertTrue(
            "Settings explicit FQN list should contain com.example.MyScript after the fix; actual=${state.parsedClassNames}",
            "com.example.MyScript" in state.parsedClassNames
        )
    }

    private fun inspectionFixNames(): List<String> =
        myFixture.availableIntentions.map(IntentionAction::getText)
}
