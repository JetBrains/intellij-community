// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime

const val KOTLIN_BUNDLED: String = "KOTLIN_BUNDLED"

private val NOT_CACHED_YET_FILE_TIME: FileTime = FileTime.fromMillis(0L)
private val FILE_DOESNT_EXIST_FILE_TIME: FileTime = FileTime.fromMillis(1L)

private class KotlinBundledPathMacroContributor : ProjectWidePathMacroContributor, ProjectCloseListener {
    private val cachedPaths = ConcurrentHashMap<String, Pair<String, FileTime>>()

    override fun getProjectPathMacros(projectFilePath: String): Map<String, String> {
        // It's not possible to use KotlinJpsPluginSettings.getInstance(project) because the project isn't yet initialized
        val iprOrKotlincXml = Path.of(projectFilePath)
            .let { iprOrMisc ->
                when (iprOrMisc.extension) {
                    ProjectFileType.DEFAULT_EXTENSION -> iprOrMisc
                    "xml" -> iprOrMisc.resolveSibling(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
                    else -> error("projectFilePath should be either misc.xml or *.ipr file")
                }
            }
        val cached = cachedPaths[projectFilePath] ?: Pair("", NOT_CACHED_YET_FILE_TIME)
        val lastModified = iprOrKotlincXml.lastModifiedOrNull() ?: FILE_DOESNT_EXIST_FILE_TIME
        val path = if (cached.second >= lastModified) {
            cached.first
        } else {
            val newPath = KotlinJpsPluginSettings.readFromKotlincXmlOrIpr(iprOrKotlincXml)?.version
                ?.let { KotlinArtifactsDownloader.getUnpackedKotlinDistPath(it).canonicalPath }
                ?: KotlinPluginLayout.kotlinc.canonicalPath
            val newPair = Pair(newPath, lastModified)
            cachedPaths
                .compute(projectFilePath) { _, oldPair ->
                    if (oldPair == null) newPair else maxOf(oldPair, newPair, compareBy { it.second })
                }
                ?.first
                ?: error("It can't be null because lambda that we pass to `compute` doesn't return `null`")
        }
        return mapOf(KOTLIN_BUNDLED to path)
    }

  private fun Path.lastModifiedOrNull(): FileTime? =
    try {
      getLastModifiedTime()
    }
    catch (ex: IOException) {
      null
    }

    override fun projectClosed(project: Project) {
        val projectFilePath = project.projectFilePath ?: return
        cachedPaths.remove(projectFilePath)
    }
}
