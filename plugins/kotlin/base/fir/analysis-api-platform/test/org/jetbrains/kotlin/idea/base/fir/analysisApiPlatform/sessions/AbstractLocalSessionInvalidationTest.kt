// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirModuleSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.idea.util.publishModuleOutOfBlockModificationEvent

/**
 * Checks that the correct sessions are invalidated after publishing modification events for select modules, determined by the test project
 * structure.
 */
abstract class AbstractLocalSessionInvalidationTest : AbstractSessionInvalidationTest() {
    override fun publishModificationEvents() {
        val modulesToMakeOOBM = testProjectStructure.modulesToMakeOOBM.map(modulesByName::getValue)
        runWriteAction {
            modulesToMakeOOBM.forEach { module ->
                module.publishModuleOutOfBlockModificationEvent()
            }
        }
    }

    override fun checkSessions(
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        checkInvalidatedModules(sessionsBeforeModification, sessionsAfterModification)
        checkSessionsMarkedInvalid(sessionsBeforeModification, sessionsAfterModification)
    }

    private fun checkInvalidatedModules(
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
                val module = moduleSession.ktModule as KaSourceModule
                module.name
            }
            .distinct()
            .sorted()

        assertEquals(testProjectStructure.expectedInvalidatedModules, changedSessionsModuleNames)
    }
}
