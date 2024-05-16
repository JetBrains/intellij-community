// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

@TestRoot("gradle/gradle-java/tests.k2")
@RunWith(JUnit3RunnerWithInners::class)
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation")
class KotlinGradleNavigationTest : AbstractGradleCodeInsightTest() {

    private val actionName: String get() = "GotoDeclaration"

    @TestMetadata("projectDependency.test")
    fun testProjectDependency() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inGroovy.test")
    fun testPluginPrecompiledInGroovy() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inKotlin.test")
    fun testPluginPrecompiledInKotlin() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inKotlinWithPackage.test")
    fun testPluginPrecompiledInKotlinWithPackage() {
        verifyNavigationFromCaretToExpected()
    }

    private fun verifyNavigationFromCaretToExpected() {
        fixture.performEditorAction(actionName)

        val text = document.text
        assertTrue("Actual text:\n\n$text", text.startsWith("// EXPECTED"))
    }

}