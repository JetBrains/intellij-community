package org.jetbrains.kotlin.idea.configuration

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

internal class ResolveModulesPerSourceSetInMppBuildIssue(
    private val projectRefresher: ProjectRefresher = ReimportGradleProjectRefresher
) : BuildIssue {

    interface ProjectRefresher {
        operator fun invoke(project: Project): CompletableFuture<*>
    }

    private val quickFix  = object: BuildIssueQuickFix {
        override val id: String = "MppNotResolveModulePerSourceSetBuildIssue.QuickFix"

        override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
            runWriteAction {
                project.gradleProjectSettings.forEach { it.isResolveModulePerSourceSet = true }
            }
            return projectRefresher(project)
        }
    }

    override val title: String
        get() = KotlinBundle.message("configuration.is.resolve.module.per.source.set")

    override val description: String =
        "Kotlin Multiplatform Projects require resolving modules per source set\n" +
                " - <a href=\"${quickFix.id}\">Fix and re-import project</a>\n"

    override val quickFixes: List<BuildIssueQuickFix> = listOf(quickFix)

    override fun getNavigatable(project: Project): Navigatable? = null
}

private object ReimportGradleProjectRefresher: ResolveModulesPerSourceSetInMppBuildIssue.ProjectRefresher {
    override fun invoke(project: Project): CompletableFuture<*> = CompletableFuture<Nothing>().apply {
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).callback(
            object : ExternalProjectRefreshCallback {
                override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                    complete(null)
                }

                override fun onFailure(errorMessage: String, errorDetails: String?) {
                    completeExceptionally(RuntimeException(errorMessage))
                }
            }
        ))
    }
}


private val Project.gradleProjectSettings: List<GradleProjectSettings>
    get() = GradleSettings.getInstance(this).linkedProjectsSettings.toList()
