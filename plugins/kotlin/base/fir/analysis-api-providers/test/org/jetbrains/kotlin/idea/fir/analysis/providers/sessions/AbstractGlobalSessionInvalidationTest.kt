// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.idea.test.projectStructureTest.ModulesByName
import org.jetbrains.kotlin.idea.test.projectStructureTest.ProjectLibrariesByName

/**
 * Checks that *all* sessions for all modules in the test project structure are invalidated after publishing a global module state
 * modification event.
 */
abstract class AbstractGlobalSessionInvalidationTest : AbstractSessionInvalidationTest() {
    override fun publishModificationEvents(
        testStructure: SessionInvalidationTestProjectStructure,
        projectLibrariesByName: ProjectLibrariesByName,
        modulesByName: ModulesByName,
    ) {
        runWriteAction {
            KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }

    override fun checkSessions(
        testStructure: SessionInvalidationTestProjectStructure,
        sessionsBeforeModification: List<LLFirSession>,
        sessionsAfterModification: List<LLFirSession>,
    ) {
        assertDoesntContain(sessionsAfterModification, sessionsBeforeModification)
        checkSessionsMarkedInvalid(sessionsBeforeModification, sessionsAfterModification)
    }
}
