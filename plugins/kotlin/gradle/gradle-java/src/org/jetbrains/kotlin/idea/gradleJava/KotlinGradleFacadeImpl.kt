// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.module.Module
import icons.GradleIcons
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.extensions.gradle.KotlinGradleConstants
import org.jetbrains.kotlin.idea.gradleJava.inspections.getResolvedVersionByModuleData
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import javax.swing.Icon

// Gradle path (example): ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-runtime/<version>
private const val KOTLIN_PLUGIN_PATH_MARKER = "${KotlinGradleConstants.GROUP_ID}/${KotlinGradleConstants.GRADLE_PLUGIN_ID}/"

// Maven local repo path (example): ~/.m2/repository/org/jetbrains/kotlin/kotlin-runtime/<version>
private val KOTLIN_PLUGIN_PATH_MARKER_FOR_MAVEN_LOCAL_REPO =
    "${KotlinGradleConstants.GROUP_ID.replace('.', '/')}/${KotlinGradleConstants.GRADLE_PLUGIN_ID}/"

internal class KotlinGradleFacadeImpl : KotlinGradleFacade {
    override val gradleIcon: Icon
        get() = GradleIcons.Gradle

    override val runConfigurationFactory: ConfigurationFactory
        get() = GradleExternalTaskConfigurationType.getInstance().configurationFactories[0]

    override fun isDelegatedBuildEnabled(module: Module): Boolean {
        return GradleProjectSettings.isDelegatedBuildEnabled(module)
    }

    override fun findKotlinPluginVersion(node: DataNode<ModuleData>): IdeKotlinVersion? {
        val buildScriptClasspathData = node.findAll(BuildScriptClasspathData.KEY).firstOrNull()?.data ?: return null
        return findKotlinPluginVersion(buildScriptClasspathData)
    }

    override fun findLibraryVersionByModuleData(node: DataNode<*>, groupId: String, libraryIds: List<String>): String? {
        return node.getResolvedVersionByModuleData(groupId, libraryIds)
    }
}

internal fun findKotlinPluginVersion(classpathData: BuildScriptClasspathData): IdeKotlinVersion? {
    for (classPathEntry in classpathData.classpathEntries.asReversed()) {
        for (path in classPathEntry.classesFile) {
            val uniformedPath = path.replace('\\', '/')
            // check / for local maven repo, and '.' for gradle
            if (uniformedPath.contains(KOTLIN_PLUGIN_PATH_MARKER)) {
                val versionSubstring = uniformedPath.substringAfter(KOTLIN_PLUGIN_PATH_MARKER).substringBefore('/', "<error>")
                if (versionSubstring != "<error>") {
                    return IdeKotlinVersion.opt(versionSubstring)
                }
            } else if (uniformedPath.contains(KOTLIN_PLUGIN_PATH_MARKER_FOR_MAVEN_LOCAL_REPO)) {
                val versionSubstring =
                    uniformedPath.substringAfter(KOTLIN_PLUGIN_PATH_MARKER_FOR_MAVEN_LOCAL_REPO).substringBefore('/', "<error>")
                if (versionSubstring != "<error>") {
                    return IdeKotlinVersion.opt(versionSubstring)
                }
            }
        }
    }

    return null
}