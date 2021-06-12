package org.jetbrains.kotlin.gradle

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.idea.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.plugins.gradle.service.project.open.setupGradleSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class ResolveModulesPerSourceSetInMppBuildIssueTest : LightPlatformTestCase() {

    override fun tearDown() {
        GradleSettings.getInstance(project).apply {
            linkedProjectsSettings.forEach { projectSetting ->
                unlinkExternalProject(projectSetting.externalProjectPath)
            }
        }
        super.tearDown()
    }


    fun `test description contains QuickFix id`() {
        val buildIssue = ResolveModulesPerSourceSetInMppBuildIssue()
        assertTrue(
            "Expected link to the QuickFix in the build issues description",
            buildIssue.quickFixes.single().id in buildIssue.description
        )
    }

    fun `test quickFix updates GradleProjectSettings`() {
        val gradleSettings = GradleSettings.getInstance(project)
        gradleSettings.setupGradleSettings()
        gradleSettings.linkProject(GradleProjectSettings().apply {
            externalProjectPath = project.basePath
            isResolveModulePerSourceSet = false
        })

        assertFalse(
            "Expected isResolveModulePerSourceSet is false before running QuickFix",
            gradleSettings.linkedProjectsSettings.single().isResolveModulePerSourceSet
        )

        val testProjectRefresher = TestProjectRefresher()
        ResolveModulesPerSourceSetInMppBuildIssue(testProjectRefresher)
            .quickFixes.single()
            .runQuickFix(project, DataProvider {})
            .get()

        assertTrue(
            "Expected isResolveModulePerSourceSet is true after running QuickFix",
            gradleSettings.linkedProjectsSettings.single().isResolveModulePerSourceSet
        )

        assertEquals(
            "Expect single invocation of project refresher",
            1, testProjectRefresher.invocationCount.get()
        )
    }
}

private class TestProjectRefresher(
) : ResolveModulesPerSourceSetInMppBuildIssue.ProjectRefresher {
    val invocationCount = AtomicInteger(0)
    override fun invoke(project: Project): CompletableFuture<*> {
        invocationCount.getAndIncrement()
        return CompletableFuture.completedFuture(null)
    }
}
