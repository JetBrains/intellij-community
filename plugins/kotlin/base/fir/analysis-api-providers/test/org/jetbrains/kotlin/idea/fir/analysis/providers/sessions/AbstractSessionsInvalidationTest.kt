// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.google.gson.JsonObject
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructure
import org.jetbrains.kotlin.idea.fir.analysis.providers.publishOutOfBlockModification
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import java.io.File
import org.jetbrains.kotlin.idea.fir.analysis.providers.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectLibrary
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructureParser

abstract class AbstractSessionsInvalidationTest : AbstractProjectStructureTest<SessionInvalidationTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("sessionInvalidation")

    protected fun doTest(path: String) {
        val (testStructure, projectLibrariesByName, modulesByNames) =
            initializeProjectStructure(path, SessionInvalidationTestProjectStructureParser)

        val rootIdeaModule = modulesByNames.getValue(testStructure.rootModule)
        val rootModule = rootIdeaModule.getMainKtSourceModule()!!

        val sessionsBeforeOOBM = getAllModuleSessions(rootModule)

        val modulesToMakeOOBM = testStructure.modulesToMakeOOBM.map(modulesByNames::getValue)
        modulesToMakeOOBM.forEach { it.publishOutOfBlockModification() }

        val sessionsAfterOOBM = getAllModuleSessions(rootModule)

        val changedSessions = buildSet {
            addAll(sessionsBeforeOOBM)
            addAll(sessionsAfterOOBM)
            removeAll(sessionsBeforeOOBM.intersect(sessionsAfterOOBM.toSet()))
        }

        val changedSessionsModuleNames = changedSessions
            .map { session ->
                val moduleSession = session as LLFirModuleSession
                val module = moduleSession.ktModule as KtSourceModule
                module.moduleName
            }
            .distinct()
            .sorted()

        assertEquals(testStructure.expectedInvalidatedModules, changedSessionsModuleNames)
    }

    private fun getAllModuleSessions(mainModule: KtModule): List<LLFirSession> {
        val projectModules = ModuleManager.getInstance(project).modules
            .flatMap { it.sourceModuleInfos }
            .map { it.toKtModule() }

        val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSession(mainModule)
        return projectModules.map(resolveSession::getSessionFor)
    }
}

data class SessionInvalidationTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val rootModule: String,
    val modulesToMakeOOBM: List<String>,
    val expectedInvalidatedModules: List<String>,
) : TestProjectStructure

private object SessionInvalidationTestProjectStructureParser : TestProjectStructureParser<SessionInvalidationTestProjectStructure> {
    private const val ROOT_MODULE_FIELD = "rootModule"
    private const val MODULES_TO_MAKE_OOBM_IN_FIELD = "modulesToMakeOOBM"
    private const val EXPECTED_INVALIDATED_MODULES_FIELD = "expectedInvalidatedModules"

    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): SessionInvalidationTestProjectStructure =
        SessionInvalidationTestProjectStructure(
            libraries,
            modules,
            json.getString(ROOT_MODULE_FIELD),
            json.getAsJsonArray(MODULES_TO_MAKE_OOBM_IN_FIELD)!!.map { it.asString }.sorted(),
            json.getAsJsonArray(EXPECTED_INVALIDATED_MODULES_FIELD)!!.map { it.asString }.sorted(),
        )
}
