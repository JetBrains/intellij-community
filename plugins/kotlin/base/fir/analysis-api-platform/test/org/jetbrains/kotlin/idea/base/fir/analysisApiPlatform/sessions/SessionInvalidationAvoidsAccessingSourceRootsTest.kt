// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions

import com.intellij.openapi.application.runWriteAction
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.workspaceModel.ide.legacyBridge.findSnapshotModuleEntity
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionCache
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionInvalidationService
import org.jetbrains.kotlin.idea.base.fir.projectStructure.createKaSourceModuleWithCustomBaseContentScope
import org.jetbrains.kotlin.idea.base.fir.projectStructure.isCustomSourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.registerAsMock
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModules
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import java.io.File

/**
 * [SessionInvalidationAvoidsAccessingSourceRootsTest] ensures that session invalidation does not request the source roots of *dependent*
 * [KaSourceModule][org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule]s. We want to avoid source root calculation of module
 * dependents for performance reasons in IntelliJ (see KTIJ-34177).
 */
class SessionInvalidationAvoidsAccessingSourceRootsTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    @OptIn(KaImplementationDetail::class, LLFirInternals::class)
    fun `test that session invalidation does not request the source roots of dependent modules`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")

        moduleB.addDependency(moduleA)

        // We want to check that `moduleB`'s source roots aren't accessed via any of its `KaSourceModule`s. Since source roots in
        // IJ `KaSourceModule`s are only used to build the base content scope, we register a mock source module that prohibits access to its
        // base content scope.
        val moduleIdB = moduleB.findSnapshotModuleEntity()?.symbolicId ?: error("Cannot find module entity for module B")
        mockDependentSourceModule(moduleIdB, KaSourceModuleKind.PRODUCTION)
        mockDependentSourceModule(moduleIdB, KaSourceModuleKind.TEST)

        moduleB.toKaSourceModules().forEach { kaModule ->
            assertTrue("Expected '$kaModule' to be a custom (mocked) source module.", kaModule.isCustomSourceModule())
        }

        // We have to create sessions for `moduleA`. Otherwise, session invalidation will short-circuit and not invalidate any dependents.
        val sessionCache = LLFirSessionCache.getInstance(project)
        moduleA.toKaSourceModules().forEach { sessionCache.getSession(it, preferBinary = false) }

        // We have to invoke session invalidation directly. Otherwise, session invalidation will be executed in a message bus chain, where
        // module caching will be disabled, which will mean that regular source modules are returned instead of the mocked modules.
        val sessionInvalidationService = LLFirSessionInvalidationService.getInstance(project)
        moduleA.toKaSourceModules().forEach { kaModule ->
            runWriteAction {
                sessionInvalidationService.invalidate(
                    KotlinModuleStateModificationEvent(kaModule, KotlinModuleStateModificationKind.UPDATE),
                )
            }
        }

        // The test passes automatically if no failures are encountered during session invalidation.
    }

    private fun mockDependentSourceModule(moduleId: ModuleId, kind: KaSourceModuleKind) {
        val kaModule = createKaSourceModuleWithCustomBaseContentScope(moduleId, kind, project) {
            fail("Session invalidation should not access the base content scope of a dependent `KaSourceModule`.")
            error("Unreachable...")
        }
        kaModule.registerAsMock(moduleId)
    }
}
