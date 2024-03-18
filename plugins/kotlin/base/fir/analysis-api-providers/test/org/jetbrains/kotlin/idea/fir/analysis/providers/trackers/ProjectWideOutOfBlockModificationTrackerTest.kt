// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.trackers

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert
import java.io.File

class ProjectWideOutOfBlockModificationTrackerTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override fun isFirPlugin(): Boolean = true

    fun `test that the project-wide out-of-block modification tracker remains unchanged after changing a non-physical file`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(FileWithText("main.kt", "fun main() {}"))
        }

        val projectModificationCountChecker = createModificationCountChecker(myProject)

        runWriteAction {
            val nonPhysicalPsi = KtPsiFactory(moduleA.project).createFile("nonPhysical", "val a = c")
            nonPhysicalPsi.add(KtPsiFactory(moduleA.project).createFunction("fun x(){}"))
        }

        Assert.assertFalse(
            "Out-of-block modification count for the project should not change after a non-physical file change, modification count is ${projectModificationCountChecker.modificationCount}",
            projectModificationCountChecker.hasChanged()
        )
    }
    
    private fun createModificationCountChecker(project: Project): ProjectOutOfBlockModificationCountChecker =
        ProjectOutOfBlockModificationCountChecker(project.createProjectWideOutOfBlockModificationTracker())

    class ProjectOutOfBlockModificationCountChecker(private val modificationTracker: ModificationTracker) {
        private val initialModificationCount = modificationTracker.modificationCount

        val modificationCount: Long get() = modificationTracker.modificationCount

        fun hasChanged(): Boolean = modificationTracker.modificationCount != initialModificationCount
    }
}
