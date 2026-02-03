// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.plugincomponents

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.takeWhileInclusive
import org.ec4j.core.EditorConfigLoader
import org.ec4j.core.PropertyTypeRegistry
import org.ec4j.core.Resource
import org.ec4j.core.ResourceProperties
import org.ec4j.core.model.Ec4jPath
import org.ec4j.core.model.EditorConfig
import org.ec4j.core.model.Version
import org.ec4j.core.parser.ParseException
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.Utils
import org.editorconfig.core.ec4jwrappers.EditorConfigLoadErrorHandler
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class EditorConfigPropertiesService(private val project: Project) : SimpleModificationTracker() {
  companion object {
    private val LOG = thisLogger()

    @JvmStatic
    fun getInstance(project: Project): EditorConfigPropertiesService = project.service()
  }

  // Keys are URLs of the containing directories
  private val editorConfigsCache = ConcurrentHashMap<String, LoadEditorConfigResult>()

  fun getRootDirs(): Set<VirtualFile> {
    if (!EditorConfigRegistry.shouldStopAtProjectRoot()) {
      return emptySet()
    }
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      val dirs: MutableSet<VirtualFile> = HashSet()
      ReadAction.run<RuntimeException> {
        dirs.addAll(project.getBaseDirectories())
      }
      LocalFileSystem.getInstance().findFileByPath(PathManager.getConfigPath())?.let { dirs.add(it) }
      CachedValueProvider.Result(
        dirs,
        ProjectRootModificationTracker.getInstance(project)
      )
    }
  }

  private fun loadEditorConfig(file: VirtualFile): LoadEditorConfigResult =
    try {
      ValidEditorConfig(file, parseEditorConfig(file.readText()))
        .also { LOG.debug { "found config: ${file.parent.url}" } }
    }
    catch (e: IOException) {
      LOG.warn(e)
      InvalidEditorConfig(file)
    }
    catch (e: ParseException) {
      LOG.debug(e)
      InvalidEditorConfig(file)
    }

  private fun parentDirsFrom(file: VirtualFile): Sequence<VirtualFile> {
    val rootDirs = getRootDirs()
    return generateSequence(if (file.isDirectory) file else file.parent) { it.parent }
      .takeWhileInclusive { it !in rootDirs }
  }

  private fun relevantEditorConfigsFor(file: VirtualFile): List<ValidEditorConfig>  {
    val result = mutableListOf<ValidEditorConfig>()
    for (dir in parentDirsFrom(file)) {
      val cachedEditorConfig = editorConfigsCache.compute(dir.url) { _, cached ->
        if (cached != null) {
          LOG.debug { "cached config: ${dir.url}" }
          return@compute cached
        }
        dir.findChild(Utils.EDITOR_CONFIG_FILE_NAME)?.let { loadEditorConfig(it) }
      }
      when (cachedEditorConfig) {
        is InvalidEditorConfig -> {
          break
        }
        is ValidEditorConfig -> {
          result += cachedEditorConfig
          if (cachedEditorConfig.parsed.isRoot) {
            LOG.debug { "reached root config: ${dir.url}" }
            break
          }
        }
        null -> {
          // .editorconfig not found in this dir, continue
        }
      }
    }
    return result
  }

  fun getProperties(file: VirtualFile): ResourceProperties = mergeEditorConfigs(file.path, relevantEditorConfigsFor(file))

  fun getPropertiesAndEditorConfigs(file: VirtualFile): Pair<ResourceProperties, List<VirtualFile>> {
    val editorConfigs = relevantEditorConfigsFor(file)
    val properties = mergeEditorConfigs(file.path, editorConfigs)
    return Pair(properties, editorConfigs.map(ValidEditorConfig::file))
  }

  internal fun clearCache() {
    editorConfigsCache.clear()
  }
}

private val EMPTY_PROPERTIES = ResourceProperties.builder().build()

private sealed interface LoadEditorConfigResult

private data class ValidEditorConfig(val file: VirtualFile, val parsed: EditorConfig) : LoadEditorConfigResult

private data class InvalidEditorConfig(val file: VirtualFile) : LoadEditorConfigResult

private fun parseEditorConfig(text: String): EditorConfig {
  val loader = makeLoader()
  return loader.load(Resource.Resources.ofString(".editorconfig", text))
}

private fun makeLoader(): EditorConfigLoader =
  EditorConfigLoader.of(Version.CURRENT, PropertyTypeRegistry.default_(), EditorConfigLoadErrorHandler())

/**
 * @param editorConfigs the relevant [EditorConfig]s, starting with the one closest to the file for which properties are polled, ending with
 * the root
 */
private fun mergeEditorConfigs(queriedFilePath: String, editorConfigs: List<ValidEditorConfig>): ResourceProperties =
  editorConfigs
    .foldRight(ResourceProperties.builder()) { (editorConfigFile, parsedEditorConfig), builder ->
      val containingDir = editorConfigFile.parent ?: return EMPTY_PROPERTIES
      val queriedFileRelativeEc4jPath = Ec4jPath.Ec4jPaths.of(
        FileUtil.getRelativePath(containingDir.path,
                                 queriedFilePath,
                                 '/',
                                 containingDir.fileSystem.isCaseSensitive) ?: return EMPTY_PROPERTIES
      )
      for (section in parsedEditorConfig.sections) {
        if (section.match(queriedFileRelativeEc4jPath)) {
          builder.properties(section.properties)
        }
      }
      builder
    }
    .build()