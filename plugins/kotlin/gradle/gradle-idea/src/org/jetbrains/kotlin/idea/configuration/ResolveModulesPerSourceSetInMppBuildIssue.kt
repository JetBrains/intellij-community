package org.jetbrains.kotlin.idea.configuration

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.CompletableFuture

internal object ResolveModulesPerSourceSetInMppBuildIssue : BuildIssue {

    @Suppress("DialogTitleCapitalization")
    override val title: String get() = KotlinBundle.message("configuration.is.resolve.module.per.source.set")


    override val description: String =
        "Kotlin Multiplatform Projects require resolving modules per source set\n" +
                " - <a href=\"${QuickFix.id}\">Fix and re-import project</a>\n"

    override val quickFixes: List<BuildIssueQuickFix> = listOf(QuickFix)

    override fun getNavigatable(project: Project): Navigatable? = null

    internal object QuickFix : BuildIssueQuickFix {
        override val id: String = "MppNotResolveModulePerSourceSetBuildIssue.QuickFix"
        override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
            val nothing = CompletableFuture<Nothing>()
            project.gradleProjectSettings.forEach { it.isResolveModulePerSourceSet = true }
            ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
            return nothing
        }
    }
}

private val Project.gradleProjectSettings: List<GradleProjectSettings>
    get() = GradleSettings.getInstance(this).linkedProjectsSettings.toList()
