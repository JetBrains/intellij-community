// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.InTextDirectivesUtils
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

    @TestMetadata("librarySourceDependency.test")
    fun testLibrarySourceDependency() {
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
    
    @TestMetadata("pluginPrecompiled/inKotlinLocatedInJavaDir.test")
    fun testPluginPrecompiledInKotlinLocatedInJavaDir() {
        verifyNavigationFromCaretToExpected()
    }

    private fun verifyNavigationFromCaretToExpected() {
        val expectedNavigationText = InTextDirectivesUtils.findStringWithPrefixes(document.text, "// \"EXPECTED-NAVIGATION-SUBSTRING\": ")

        fixture.performEditorAction(actionName)

        val text = document.text
        if (expectedNavigationText != null) {
            assertTrue("Actual text:\n\n$text", text.contains(expectedNavigationText))
        } else {
            assertTrue("Actual text:\n\n$text", text.startsWith("// EXPECTED"))
        }
    }

}