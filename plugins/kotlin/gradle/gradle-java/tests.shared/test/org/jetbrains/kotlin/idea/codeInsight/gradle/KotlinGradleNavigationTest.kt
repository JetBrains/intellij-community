// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith
import java.io.File
import kotlin.reflect.KMutableProperty0

private const val SCRIPTING_ENABLED_FLAG = "kotlin.k2.scripting.enabled"

@TestRoot("gradle/gradle-java/tests.k2")
@RunWith(JUnit3RunnerWithInners::class)
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation")
class KotlinGradleNavigationTest : KotlinGradleImportingTestCase() {

    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    private val actionName: String get() = "GotoDeclaration"

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
    }

    override fun setUp() {
        gradleVersion = "8.6"
        Registry.get(SCRIPTING_ENABLED_FLAG).setValue(true)
        super.setUp()
    }

    override fun tearDownFixtures() {
        runAll(
            ThrowableRunnable { codeInsightTestFixture.tearDown() },
            ThrowableRunnable {
                @Suppress("UNCHECKED_CAST")
                (this::codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
            },
            ThrowableRunnable { myTestFixture = null }
        )
    }

    override fun clearTextFromMarkup(text: String): String {
        return text.replace("<caret>", "")
    }

    @TestMetadata("projectDependency")
    fun testProjectDependency() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inGroovy")
    fun testPluginPrecompiledInGroovy() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inKotlin")
    fun testPluginPrecompiledInKotlin() {
        verifyNavigationFromCaretToExpected()
    }

    @TestMetadata("pluginPrecompiled/inKotlinWithPackage")
    fun testPluginPrecompiledInKotlinWithPackage() {
        verifyNavigationFromCaretToExpected()
    }

    private fun verifyNavigationFromCaretToExpected() {
        val mainKtsVirtualFile = configureByFiles()
            .filter { it.name == "build.gradle.kts" && it.parent.name == "project" }
            .firstOrNull() ?: error("main build.gradle.kts file not found")

        importProject(false)

        codeInsightTestFixture.configureFromExistingVirtualFile(mainKtsVirtualFile)

        val caretState = File(testDataDirectory().absolutePath, "build.gradle.kts").extractCaretAndSelectionMarkers()

        runInEdtAndWait {
            EditorTestUtil.setCaretsAndSelection(codeInsightTestFixture.editor, caretState)
        }

        codeInsightTestFixture.performEditorAction(actionName)

        val fileEditorManager = FileEditorManager.getInstance(codeInsightTestFixture.project) as FileEditorManagerEx
        assertTrue(fileEditorManager.selectedTextEditor?.document?.text?.startsWith("// EXPECTED") == true)
    }

    companion object {
        private fun File.extractCaretAndSelectionMarkers(): EditorTestUtil.CaretAndSelectionState {
            val document = EditorFactory.getInstance().createDocument(this.readText())
            return EditorTestUtil.extractCaretAndSelectionMarkers(document)
        }
    }

}