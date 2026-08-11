// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.asCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.valueOrNull

class ScriptConfigurationsProviderImplTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun `test cached configuration is reused for VirtualFileScriptSource`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")
        val virtualFile = file.virtualFile

        runBlocking { KotlinScriptService.getInstance(project).load(virtualFile) }
        val stored = storedConfiguration()

        val result = ScriptConfigurationsProviderImpl.getInstance(project)
            .getScriptCompilationConfiguration(VirtualFileScriptSource(virtualFile), providedConfiguration = null)

        assertTrue("Result must be a success after the configuration is cached", result is ResultWithDiagnostics.Success)
        assertEquals("Cached configuration must be reused as-is", stored, result?.valueOrNull()?.configuration)
    }

    fun `test cached configuration is reused for KtFileScriptSource`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")
        val virtualFile = file.virtualFile

        runBlocking { KotlinScriptService.getInstance(project).load(virtualFile) }
        val stored = storedConfiguration()

        val result = ScriptConfigurationsProviderImpl.getInstance(project)
            .getScriptCompilationConfiguration(KtFileScriptSource(file as KtFile), providedConfiguration = null)

        assertTrue("Result must be a success after the configuration is cached", result is ResultWithDiagnostics.Success)
        assertEquals("Cached configuration must be reused as-is", stored, result?.valueOrNull()?.configuration)
    }

    fun `test base configuration is returned without refinement when not yet cached`() {
        // addFileToProject (not configureByText) so the file is never opened: no editor event schedules a
        // background load, the entity stays absent, and we exercise the not-yet-loaded analysis window.
        val file = myFixture.addFileToProject("test.kts", "val x = 1")
        val baseConfiguration = ScriptCompilationConfiguration { }

        val result = ScriptConfigurationsProviderImpl.getInstance(project)
            .getScriptCompilationConfiguration(VirtualFileScriptSource(file.virtualFile), baseConfiguration)

        assertTrue("Result must be a success based on the provided base configuration", result is ResultWithDiagnostics.Success)
        assertEquals(
            "Un-refined base configuration must be returned unchanged when nothing is cached yet",
            baseConfiguration,
            result?.valueOrNull()?.configuration
        )
    }

    fun `test null is returned when nothing is cached and no base configuration is provided`() {
        val file = myFixture.addFileToProject("test.kts", "val x = 1")

        val result = ScriptConfigurationsProviderImpl.getInstance(project)
            .getScriptCompilationConfiguration(VirtualFileScriptSource(file.virtualFile), providedConfiguration = null)

        assertNull("Without a cached entity and without a base configuration there is nothing to return", result)
    }

    private fun storedConfiguration(): ScriptCompilationConfiguration {
        val virtualFile = myFixture.file.virtualFile
        val entity = requireNotNull(KotlinScriptEntityProvider.findKotlinScriptEntity(project, virtualFile)) {
            "KotlinScriptEntity must exist after load"
        }
        val snapshot = project.workspaceModel.currentSnapshot
        return requireNotNull(entity.configurationId?.let { snapshot.resolve(it) }?.data?.asCompilationConfiguration()) {
            "Stored compilation configuration must exist after load"
        }
    }
}
