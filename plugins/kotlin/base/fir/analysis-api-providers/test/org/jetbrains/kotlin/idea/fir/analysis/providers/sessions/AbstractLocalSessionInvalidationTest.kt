// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.test.projectStructureTest.ModulesByName
import org.jetbrains.kotlin.idea.test.projectStructureTest.ProjectLibrariesByName
import org.jetbrains.kotlin.idea.util.publishModuleOutOfBlockModification

/**
 * Checks that the correct sessions are invalidated after publishing modification events for select modules, determined by the test project
 * structure.
 */
abstract class AbstractLocalSessionInvalidationTest : AbstractSessionInvalidationTest() {
    override fun publishModificationEvents(
        testStructure: SessionInvalidationTestProjectStructure,
        projectLibrariesByName: ProjectLibrariesByName,
        modulesByName: ModulesByName,
    ) {
        val modulesToMakeOOBM = testStructure.modulesToMakeOOBM.map(modulesByName::getValue)
        runWriteAction {
            modulesToMakeOOBM.forEach { module ->
                module.publishModuleOutOfBlockModification()
            }
        }
    }

    override fun checkSessions(
        testStructure: SessionInvalidationTestProjectStructure,
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        checkInvalidatedModules(testStructure, sessionsBeforeModification, sessionsAfterModification)
        checkSessionsMarkedInvalid(sessionsBeforeModification, sessionsAfterModification)
    }

    private fun checkInvalidatedModules(
        testStructure: SessionInvalidationTestProjectStructure,
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        val changedSessions = buildSet {
            addAll(sessionsBeforeModification)
            addAll(sessionsAfterModification)
            removeAll(sessionsBeforeModification.intersect(sessionsAfterModification.toSet()))
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
}
