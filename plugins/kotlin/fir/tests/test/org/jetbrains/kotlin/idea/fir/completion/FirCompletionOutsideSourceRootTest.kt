// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.util.invalidateCaches
import java.nio.charset.Charset

class FirCompletionOutsideSourceRootTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testFunctionName() {
        val tempFile = createTempFile("kt", null, "fun <caret>", Charset.defaultCharset())
        myFixture.configureFromExistingVirtualFile(tempFile)
        myFixture.completeBasic()
    }

    fun testOverrideHandler() {
        val tempFile = createTempFile("kt", null, "interface A {fu<caret>}", Charset.defaultCharset())
        myFixture.configureFromExistingVirtualFile(tempFile)
        myFixture.completeBasic()
    }

    fun testInFunctionBody() {
        val tempFile = createTempFile("kt", null, "fun foo() {val Str<caret>}", Charset.defaultCharset())
        myFixture.configureFromExistingVirtualFile(tempFile)
        myFixture.completeBasic()
    }

    fun testInLambda() {
        val tempFile = createTempFile("kt", null, "fun f(): () -> Unit {  return {\"\".<caret> }}", Charset.defaultCharset())
        myFixture.configureFromExistingVirtualFile(tempFile)
        myFixture.completeBasic()
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}