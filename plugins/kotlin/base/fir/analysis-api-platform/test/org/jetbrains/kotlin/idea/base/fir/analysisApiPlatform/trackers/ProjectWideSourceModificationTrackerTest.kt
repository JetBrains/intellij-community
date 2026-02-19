// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.trackers

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analysis.api.platform.modification.createProjectWideSourceModificationTracker
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert
import java.io.File

class ProjectWideSourceModificationTrackerTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun `test that the project-wide source modification tracker remains unchanged after changing a non-physical file`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(FileWithText("main.kt", "fun main() {}"))
        }

        val projectModificationCountChecker = createModificationCountChecker(myProject)

        runWriteAction {
            val nonPhysicalPsi = KtPsiFactory(moduleA.project).createFile("nonPhysical", "val a = c")
            nonPhysicalPsi.add(KtPsiFactory(moduleA.project).createFunction("fun x(){}"))
        }

        Assert.assertFalse(
            "The source modification count for the project should not change after a non-physical file change, modification count is ${projectModificationCountChecker.modificationCount}",
            projectModificationCountChecker.hasChanged()
        )
    }
    
    private fun createModificationCountChecker(project: Project): ProjectSourceModificationCountChecker =
        ProjectSourceModificationCountChecker(project.createProjectWideSourceModificationTracker())

    class ProjectSourceModificationCountChecker(private val modificationTracker: ModificationTracker) {
        private val initialModificationCount = modificationTracker.modificationCount

        val modificationCount: Long get() = modificationTracker.modificationCount

        fun hasChanged(): Boolean = modificationTracker.modificationCount != initialModificationCount
    }
}
