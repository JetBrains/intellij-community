// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.plugincomponents

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.SlowOperations
import kotlinx.coroutines.*
import org.ec4j.core.*
import org.ec4j.core.model.Version
import org.ec4j.core.parser.ParseException
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.core.ec4jwrappers.EditorConfigLoadErrorHandler
import org.editorconfig.core.ec4jwrappers.EditorConfigPermanentCache
import org.editorconfig.core.ec4jwrappers.VirtualFileResource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
class SettingsProviderComponent(private val project: Project, private val coroutineScope: CoroutineScope) : SimpleModificationTracker() {
  companion object {
    private val LOG = thisLogger()
    private const val PROCESSING_POOL_SIZE = 16
    private const val TIMEOUT = 10 // Seconds
    private val EMPTY_PROPERTIES = ResourceProperties.builder().build()

    @JvmStatic
    fun getInstance(project: Project): SettingsProviderComponent = project.service()
  }

  internal val resourceCache = EditorConfigPermanentCache()

  private val resourcePropertiesService = ResourcePropertiesService.builder()
    .configFileName(EditorConfigConstants.EDITORCONFIG)
    .rootDirectories(getRootDirs().map { VirtualFileResource(it) })
    .cache(resourceCache)
    .loader(EditorConfigLoader.of(Version.CURRENT, PropertyTypeRegistry.default_(), EditorConfigLoadErrorHandler()))
    // TODO custom handling of unset values?
    .keepUnset(true)
    .build()

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

  fun getProperties(file: VirtualFile): ResourceProperties {
    return try {
      SlowOperations.knownIssue("IDEA-307301, EA-775214").use {
        resourcePropertiesService.queryProperties(VirtualFileResource(file))
      }
    } catch (e: IOException) {
      // error reading from an .editorconfig file
      LOG.warn(e)
      EMPTY_PROPERTIES
    } catch (e: ParseException) {
      // syntax error (we're using ErrorHandler.THROW_SYNTAX_ERRORS_IGNORE_OTHERS)
      LOG.debug(e)
      EMPTY_PROPERTIES
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val processingDispatcher = Dispatchers.IO.limitedParallelism(PROCESSING_POOL_SIZE)

  private val pendingJobs = ConcurrentHashMap<String, Lazy<Deferred<Pair<ResourceProperties, List<VirtualFile>>>>>()

  private fun doGetPropertiesAndEditorConfigs(file: VirtualFile): Pair<ResourceProperties, List<VirtualFile>> {
    var properties: ResourceProperties? = null
    val accessed = resourceCache.doWhileRecordingAccess {
      properties = getProperties(file)
    }.map {
      require(it is VirtualFileResource)
      it.file
    }
    return Pair(properties!!, accessed)
  }

  suspend fun getPropertiesAndEditorConfigs(file: VirtualFile): Pair<ResourceProperties, List<VirtualFile>> {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      return doGetPropertiesAndEditorConfigs(file)
    }
    else {
      val key = file.url
      val lazyDeferred = pendingJobs.computeIfAbsent(key) {
        lazy {
          coroutineScope.async(CoroutineName("EditorConfig IO") + processingDispatcher) {
            doGetPropertiesAndEditorConfigs(file)
          }.apply {
            invokeOnCompletion { pendingJobs.remove(key) } // cleanup
          }
        }
      }
      val deferred = lazyDeferred.value
      return withTimeout(TIMEOUT.seconds) {
        deferred.await()
      }
    }
  }
}