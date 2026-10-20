// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinScriptDefinitionLineMarkerProviderTest : NewLightKotlinCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String = ""

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor(
        listOf(TestKotlinArtifacts.kotlinStdlib, TestKotlinArtifacts.kotlinScriptingCommon),
        emptyList(),
    )

    fun testGutterOnClass() {
        assertEquals(1, scriptDefinitionGutterCount("class MyScript"))
    }

    fun testGutterOnObject() {
        assertEquals(1, scriptDefinitionGutterCount("object MyScript"))
    }

    fun testNoGutterOnObjectWithoutAnnotation() {
        myFixture.configureByText(KotlinFileType.INSTANCE, "package com.example\nobject PlainObject")
        assertEquals(0, countScriptDefinitionGutters())
    }

    private fun scriptDefinitionGutterCount(declaration: String): Int {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
                package com.example
                import kotlin.script.experimental.annotations.KotlinScript
                @KotlinScript
                $declaration
            """.trimIndent()
        )
        return countScriptDefinitionGutters()
    }

    private fun countScriptDefinitionGutters(): Int {
        val missingMarker = KotlinBaseScriptingBundle.message("gutter.script.definition.setup.marker.missing")
        return myFixture.findAllGutters().count { it.tooltipText?.contains(missingMarker) == true }
    }
}
