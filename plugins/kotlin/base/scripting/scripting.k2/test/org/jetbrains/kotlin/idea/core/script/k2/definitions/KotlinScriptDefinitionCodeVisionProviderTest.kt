// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.k2.kotlinScriptDefinitionInlayHint
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class KotlinScriptDefinitionCodeVisionProviderTest : KotlinLightCodeInsightFixtureTestCase() {

    

    private val provider = KotlinScriptDefinitionCodeVisionProvider()

    fun `test computeForEditor returns empty for non-script file`() {
        myFixture.configureByText("Test.kt", "fun main() {}")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertEmpty(hints)
    }

    fun `test kts script definition hint`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertSize(1, hints)
        val (range, entry) = hints[0]
        assertEquals(TextRange(0, 0), range)
        assertEquals("Script definition: .kts", entry.longPresentation)
    }

    fun `test custom script definition hint`() {
        registerCustomDefinition()

        myFixture.configureByText("test.custom.kts", "val x = 1")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertSize(1, hints)
        val (range, entry) = hints[0]
        assertEquals(TextRange(0, 0), range)
        assertEquals("Custom Hint", entry.longPresentation)
    }

    private fun registerCustomDefinition() {
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("custom.kts")
                ide.kotlinScriptDefinitionInlayHint { "Custom Hint" }
            }
        )
        val customDefinition = ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            evaluationConfiguration
        )

        project.registerExtension(SCRIPT_DEFINITIONS_SOURCES, object : ScriptDefinitionsSource {
            override val definitions: Sequence<ScriptDefinition> = sequenceOf(customDefinition)
        }, testRootDisposable)

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    fun `test empty script file`() {
        myFixture.configureByText("empty.kts", "")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertSize(1, hints)
        assertEquals(TextRange(0, 0), hints[0].first)
    }

    fun `test comment only script file`() {
        myFixture.configureByText("comment.kts", "// just a comment")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertSize(1, hints)
        assertEquals(TextRange(0, 0), hints[0].first)
    }

    fun `test gradle script file has no hint`() {
        myFixture.configureByText("build.gradle.kts", "plugins {}")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        val (range, entry) = hints[0]
        assertEquals(TextRange(0, 0), range)
        assertEquals("Script definition: .kts", entry.longPresentation)
    }

    fun `test disabled by global kotlin registry key`() {
        Registry.get("enable.kotlin.code.vision.inlay").setValue(false, testRootDisposable)
        myFixture.configureByText("test.kts", "val x = 1")
        val hints = provider.computeForEditor(myFixture.editor, myFixture.file)
        assertEmpty(hints)
    }
}
