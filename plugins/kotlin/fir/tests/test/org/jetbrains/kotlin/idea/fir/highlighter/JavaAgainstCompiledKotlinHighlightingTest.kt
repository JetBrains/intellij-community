// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.highlighter

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

/**
 * Highlights Java code that passes a functional expression for a type parameter of a Kotlin library method.
 *
 * The Kotlin plugin shows a compiled Kotlin class as a light class. A light method delegates to a
 * `ClsMethodImpl`, so `PsiTypeParameter.getOwner()` returns the delegate and not the light method.
 * Java code insight must compare the two with [com.intellij.psi.PsiElement.isEquivalentTo].
 * Otherwise, it reports a false error on the call.
 *
 * This test needs the Kotlin plugin. Without the plugin the compiled class stays a `ClsClassImpl`,
 * and the problem does not occur.
 */
@TestRoot("idea/tests")
@TestMetadata("testData/highlighterJavaAgainstCompiledKotlin")
@RunWith(JUnit38ClassRunner::class)
class JavaAgainstCompiledKotlinHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        val libraryJar = KotlinCompilerStandalone(
            listOf(IDEA_TEST_DATA_DIR.resolve("highlighterJavaAgainstCompiledKotlin/library"))
        ).compile().toPath()

        return KotlinJdkAndLibraryProjectDescriptor(listOf(TestKotlinArtifacts.kotlinStdlib, libraryJar))
    }

    fun testImplicitlyTypedLambda() {
        doTest()
    }

    fun testExplicitlyTypedLambda() {
        doTest()
    }

    fun testOverloadedLambda() {
        doTest()
    }

    fun testOverloadedMethodReference() {
        doTest()
    }

    private fun doTest() {
        myFixture.configureByFile(getTestName(false) + ".java")
        myFixture.checkHighlighting()
    }
}
