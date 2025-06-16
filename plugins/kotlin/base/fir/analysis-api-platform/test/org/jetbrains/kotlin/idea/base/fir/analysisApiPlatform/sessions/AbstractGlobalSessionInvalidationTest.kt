// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession

/**
 * Checks that *all* sessions for all modules in the test project structure are invalidated after publishing a global module state
 * modification event.
 */
abstract class AbstractGlobalSessionInvalidationTest : AbstractSessionInvalidationTest() {
    override fun publishModificationEvents() {
        runWriteAction {
            project.publishGlobalModuleStateModificationEvent()
        }
    }

    override fun checkSessions(
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        assertDoesntContain(sessionsAfterModification, sessionsBeforeModification)
        checkSessionsMarkedInvalid(sessionsBeforeModification, sessionsAfterModification)
    }
}
