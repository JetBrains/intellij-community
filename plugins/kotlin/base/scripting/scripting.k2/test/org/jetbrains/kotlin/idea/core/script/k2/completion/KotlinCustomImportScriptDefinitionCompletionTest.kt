// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.completion

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

/**
 * A file-level import annotation for a synthetic custom script definition. Used to prove that imported-script
 * completion is definition-agnostic: it relies on the configuration's imported-scripts key, not on `main.kts`.
 */
@Target(AnnotationTarget.FILE)
annotation class CustomImport(val path: String)

/**
 * Verifies that imported-script completion works for an arbitrary script definition with its own import
 * annotation (`@file:CustomImport(...)`), which the definition's refinement handler resolves into the
 * configuration's imported-scripts key. Exercises
 * [org.jetbrains.kotlin.idea.core.script.k2.codeInsight.KotlinImportedScriptCompletionProvider] for a
 * non-`main.kts` definition.
 */
class KotlinCustomImportScriptDefinitionCompletionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
        myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
        projectBuilder.addModule(JavaModuleFixtureBuilder::class.java).addContentRoot(myFixture.tempDirPath)
        myFixture.setUp()

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)
        invalidateLibraryCache(project)

        runBlocking(Dispatchers.EDT) {
            edtWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = IdeaTestUtil.getMockJdk17()
            }
        }
    }

    fun `test imported script symbols of a custom definition are completed`() {
        registerCustomImportScriptDefinition()

        myFixture.tempDirFixture.createFile(
            "helper.kts",
            """
            fun helperFromCustom() {}

            fun helperUtility() {}

            private fun helperSecret() {}
            """.trimIndent(),
        )
        val mainFile = myFixture.tempDirFixture.createFile(
            "activate.imports.kts",
            """
            @file:CustomImport("helper.kts")

            helper<caret>
            """.trimIndent(),
        )

        runInEdtAndWait { myFixture.configureFromExistingVirtualFile(mainFile) }
        runBlocking { KotlinScriptService.getInstance(project).load(mainFile) }

        runInEdtAndWait {
            myFixture.completeBasic()
            val lookups = myFixture.lookupElementStrings.orEmpty()
            assertContainsElements(lookups, "helperFromCustom", "helperUtility")
            assertDoesntContain(lookups, "helperSecret")
        }
    }

    @Suppress("DEPRECATION") // ScriptDefinitionsSource is the registration path used by the script test fixtures (KT-82551).
    private fun registerCustomImportScriptDefinition() {
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("imports.kts")
                ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
                refineConfiguration {
                    onAnnotations(CustomImport::class) { context -> resolveImportedScripts(context) }
                }
            },
        )

        val definition = ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            evaluationConfiguration,
        )

        project.registerExtension(
            SCRIPT_DEFINITIONS_SOURCES,
            object : ScriptDefinitionsSource {
                override val definitions: Sequence<ScriptDefinition> = sequenceOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    private fun resolveImportedScripts(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val scriptDir = context.script.locationId?.let { File(it).parentFile }
            ?: return context.compilationConfiguration.asSuccess()

        val imported = context.collectedData?.get(ScriptCollectedData.collectedAnnotations).orEmpty()
            .map { it.annotation }
            .filterIsInstance<CustomImport>()
            .map { FileScriptSource(File(scriptDir, it.path)) }

        if (imported.isEmpty()) return context.compilationConfiguration.asSuccess()
        return context.compilationConfiguration.with { importScripts(imported) }.asSuccess()
    }
}
