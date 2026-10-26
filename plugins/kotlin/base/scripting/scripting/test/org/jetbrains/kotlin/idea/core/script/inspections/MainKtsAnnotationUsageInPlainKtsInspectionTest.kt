// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.inspections

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.templates.ScriptWithArgs

/**
 * Checks [MainKtsAnnotationUsageInPlainKtsInspection.isApplicableByPsi] directly.
 * A light test cannot highlight a script, because the script has no workspace model entity.
 */
class MainKtsAnnotationUsageInPlainKtsInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    private val inspection = MainKtsAnnotationUsageInPlainKtsInspection()

    fun testDependsOnInBundledDefinitionScript() = assertTrue(isApplicable("test.kts", "DependsOn"))

    fun testRepositoryInBundledDefinitionScript() = assertTrue(isApplicable("test.kts", "Repository"))

    fun testImportInBundledDefinitionScript() = assertTrue(isApplicable("test.kts", "Import"))

    fun testCustomDefinitionScript() {
        registerCustomDefinition()
        assertFalse(isApplicable("test.custom.kts", "DependsOn"))
    }

    fun testNonScriptFile() = assertFalse(isApplicable("test.kt", "DependsOn"))

    private fun isApplicable(fileName: String, annotationName: String): Boolean {
        myFixture.configureByText(fileName, "@file:$annotationName(\"org.example:demo:1.0\")\n")
        val annotationEntry = PsiTreeUtil.findChildOfType(myFixture.file, KtAnnotationEntry::class.java)!!
        return inspection.isApplicableByPsi(annotationEntry)
    }

    private fun registerCustomDefinition() {
        val customDefinition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = { fileExtension("custom.kts") }
        )

        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "MainKtsAnnotationUsageInPlainKtsInspectionTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(customDefinition)
            },
            testRootDisposable,
        )

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
