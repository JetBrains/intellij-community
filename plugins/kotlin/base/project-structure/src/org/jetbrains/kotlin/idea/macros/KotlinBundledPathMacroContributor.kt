// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.compiler.configuration.versionWithFallback
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.pathString

const val KOTLIN_BUNDLED: @NonNls String = "KOTLIN_BUNDLED"

private val cachedProjectPathToKotlinDistribution = ConcurrentHashMap<String, String>()

/**
 * See also [com.jetbrains.analyzer.kotlin.AnalyzerKotlinBundledPathMacroContributor].
 */
internal class KotlinBundledPathMacroContributor() : ProjectWidePathMacroContributor, ProjectCloseListener {
    override fun getProjectPathMacros(projectFilePath: String): Map<String, String> {
        // It's not possible to use KotlinJpsPluginSettings.getInstance(project) because the project isn't yet initialized
        val path = cachedProjectPathToKotlinDistribution.computeIfAbsent(projectFilePath) {
            val iprOrKotlincXml = Path.of(projectFilePath)
                .let { iprOrMisc ->
                    when (iprOrMisc.extension) {
                        ProjectFileType.DEFAULT_EXTENSION -> iprOrMisc
                        "xml" -> iprOrMisc.resolveSibling(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
                        else -> error("projectFilePath should be either misc.xml or *.ipr file")
                    }
                }

            fun Path.toPathString(): String = try {
                toRealPath().pathString
            } catch (_: IOException) {
                absolutePathString()
            }

            KotlinJpsPluginSettings.readFromKotlincXmlOrIpr(iprOrKotlincXml)?.versionWithFallback
                ?.let {
                    KotlinArtifactsDownloader.getUnpackedKotlinDist(it).toPathString()
                } ?: KotlinPluginLayout.kotlincPath.toPathString()
        }
        return mapOf(KOTLIN_BUNDLED to path)
    }

    override fun projectClosed(project: Project) {
        val projectFilePath = project.projectFilePath ?: return
        cachedProjectPathToKotlinDistribution.remove(projectFilePath)
    }
}

internal class KotlinBundledMacroRefresher: ProjectActivity {
    init {
        if (isUnitTestMode()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(project: Project) {
        val projectFilePath = project.projectFilePath ?: return
        WorkspaceModel.getInstance(project).eventLog.collect { event ->
            if (event.getChanges(LibraryEntity::class.java).isNotEmpty()) {
                cachedProjectPathToKotlinDistribution.remove(projectFilePath)
            }
        }
    }
}