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
  private val editorConfigsCache = ConcurrentHashMap<String, ValidEditorConfig>()

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

  private fun loadEditorConfigFromDir(dir: VirtualFile): LoadEditorConfigResult {
    return try {
      val foundEditorConfig = findAndReadEditorConfigInDir(dir)
      if (foundEditorConfig != null) {
        val (file, text) = foundEditorConfig
        ValidEditorConfig(file, parseEditorConfig(text))
      }
      else {
        NonExistentEditorConfig
      }
    }
    catch (e: IOException) {
      LOG.warn(e)
      InvalidEditorConfig(dir)
    }
    catch (e: ParseException) {
      LOG.debug(e)
      InvalidEditorConfig(dir)
    }
  }

  private fun relevantEditorConfigsFor(file: VirtualFile): List<ValidEditorConfig> {
    //assert(file.isFile)
    val rootDirs = getRootDirs()
    val result = mutableListOf<ValidEditorConfig>()
    var reachedRoot = false
    var error = false
    var dir: VirtualFile? = file.parent
    while (dir != null && !reachedRoot) {
      val maybeDirWithConfig = dir
      // due to a limitation of Kotlin, cannot use computeIfAbsent
      val cachedEditorConfig = editorConfigsCache.compute(maybeDirWithConfig.url) { _, cached ->
        if (cached != null) {
          LOG.debug { "cached config ${maybeDirWithConfig.url}" }
          return@compute cached
        }
        when (val loaded = loadEditorConfigFromDir(maybeDirWithConfig)) {
          is ValidEditorConfig -> loaded.also { LOG.debug { "found config ${maybeDirWithConfig.url}" } }
          is NonExistentEditorConfig -> null
          is InvalidEditorConfig -> {
            error = true
            null
          }
        }
      }
      if (error) {
        return emptyList()
      }

      reachedRoot = dir in rootDirs

      if (cachedEditorConfig != null) {
        result += cachedEditorConfig
        reachedRoot = reachedRoot || cachedEditorConfig.parsed.isRoot
      }

      if (reachedRoot) LOG.debug { "reached root config: ${maybeDirWithConfig.url}" }

      dir = dir.parent
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

private const val TIMEOUT = 10 // Seconds
private val EMPTY_PROPERTIES = ResourceProperties.builder().build()

private sealed interface LoadEditorConfigResult

private data class ValidEditorConfig(val file: VirtualFile, val parsed: EditorConfig) : LoadEditorConfigResult

private data class InvalidEditorConfig(val file: VirtualFile) : LoadEditorConfigResult

private data object NonExistentEditorConfig : LoadEditorConfigResult

private fun parseEditorConfig(text: String): EditorConfig {
  val loader = makeLoader()
  return loader.load(Resource.Resources.ofString(".editorconfig", text))
}

private fun makeLoader(): EditorConfigLoader =
  EditorConfigLoader.of(Version.CURRENT, PropertyTypeRegistry.default_(), EditorConfigLoadErrorHandler())

private fun findAndReadEditorConfigInDir(dir: VirtualFile): Pair<VirtualFile, String>? {
  val editorConfigFile = dir.findChild(Utils.EDITOR_CONFIG_FILE_NAME)
  return editorConfigFile?.let { Pair(it, it.readText()) }
}

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