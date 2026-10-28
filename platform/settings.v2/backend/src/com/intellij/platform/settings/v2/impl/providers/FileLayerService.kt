// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SaveResult
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.VirtualFileSettingsContext
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Backs [JsonFileLayerProvider]. A directory holds one config file per locality, such as `.jetbrains.json`. Besides
 * the own top-level keys of the directory, a config holds a synthetic [PER_FILE_CONTAINER] map from a file name to the
 * overrides of that file. So one config backs the directory context **and** one context for each named file.
 *
 * It builds on [BaseLayerService]. [split] fans a loaded directory config out into the directory layer and one layer
 * for each per-file entry, and it clears an entry that vanished. [writeData] does the inverse. Every context under the
 * directory maps to it through [storageContext], so their slices persist together in one file. [requestData] returns
 * the loaded layers for the vault to install. It finds the configs in the file name index, so it waits until the
 * index is ready (ADR 0163). The VFS watcher pushes a later external change.
 */
@Service(Service.Level.PROJECT)
internal class FileLayerService(private val project: Project, private val scope: CoroutineScope) : BaseLayerService(scope) {
  override val providerClass: Class<out LayerProvider> = JsonFileLayerProvider::class.java

  /**
   * Holds the per-file contexts that [split] last emitted for each config, keyed by the directory url and the
   * locality, then by the file name. A new parse can drop a [PER_FILE_CONTAINER] entry, or the file itself can go.
   * Then [split] pushes an empty layer to clear the override of that file, so the override does not stay stale. The
   * clear uses the remembered context, because the file may be gone from the disk and cannot resolve. A context is a
   * snapshot with no live references, so it is safe to hold.
   */
  private val currentFileConfigsMap = ConcurrentHashMap<Pair<String, LayerLocality>, Map<String, SettingsContext>>()

  /**
   * Tells whether the VFS watcher reacts to a config change. It is always `true` in production. A test sets it to
   * `false`, so that only the reload of the vault loads, and the test sees the mark-sweep with no watcher push.
   */
  @Volatile
  private var watcherEnabled = true

  @TestOnly
  fun setWatcherEnabled(enabled: Boolean) {
    watcherEnabled = enabled
  }

  init {
    // The listener is bound to this project service, so it goes away when the project closes.
    VirtualFileManager.getInstance().addAsyncFileListener(ConfigFileListener(), this)
  }

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
    if (context.realContext is VirtualFileSettingsContext) localities.toSet() else emptySet()

  /**
   * Returns the directory context whose config stores [context]. The disk decides (ADR 0121). A file stores in the
   * config of its directory, its [parentContext][SettingsContext.parentContext], and a directory owns its config. A
   * path that does not exist is its own storage context, so its save is dropped (ADR 0122).
   */
  override fun storageContext(context: SettingsContext): SettingsContext {
    val virtualFileContext = context.realContext as? VirtualFileSettingsContext ?: return context
    return if (Files.isRegularFile(virtualFileContext.nioPath)) virtualFileContext.parentContext else virtualFileContext
  }

  /**
   * Writes the directory config of [storageContext], built from the slices of the vault. This is the inverse of
   * [split]. Each per-file slice goes under [PER_FILE_CONTAINER], keyed by the name of the file.
   *
   * @return [SaveResult.DROPPED] when [storageContext] is not a directory on the disk, else [SaveResult.SAVED]
   */
  override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult {
    val directoryContext = requireNotNull(storageContext.realContext as? VirtualFileSettingsContext) {
      "storage context '${storageContext.identity}' is not a file context"
    }
    if (!withContext(Dispatchers.IO) { Files.isDirectory(directoryContext.nioPath) }) {
      logDroppedSave(storageContext, locality, slices, "it is not a directory on disk")
      return SaveResult.DROPPED
    }
    val directoryData = slices.entries.firstOrNull { it.key.identity == storageContext.identity }?.value ?: MapValue(emptyMap())
    val perFileData = slices.filterKeys { it.identity != storageContext.identity }
      .mapKeys { (context, _) ->
        val fileContext = context.realContext as? VirtualFileSettingsContext
        require(fileContext != null && fileContext.parentContext.identity == storageContext.identity) {
          "'${context.identity}' is not a file directly under '${storageContext.identity}'"
        }
        fileContext.url.substringAfterLast('/')
      }
    val model = if (perFileData.isEmpty()) directoryData else MapValue.wrapOwned(directoryData.values + (PER_FILE_CONTAINER to MapValue.wrapOwned(perFileData)))
    write(storageContext, locality, model)
    return SaveResult.SAVED
  }

  // The content goes to the disk through nio, not through the VFS cache. So a read sees the real state on the disk,
  // and a write lands as an external change. An editor with unsaved edits of the same file then shows a conflict,
  // and the edits are not silently overwritten. The VFS only finds the configs and reports their edits.
  override suspend fun read(storageContext: SettingsContext, locality: LayerLocality): MapValue {
    val path = configPath(storageContext, locality)
    return withContext(Dispatchers.IO) { JsonConfigFile.read(path) }  // throws SettingsDecodingException on malformed; the base skips the layer
  }

  override suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue) {
    val path = configPath(storageContext, locality)
    withContext(Dispatchers.IO) { JsonConfigFile.write(path, data) }
  }

  /**
   * Returns the path of the [locality] config in the directory [storageContext]: `.jetbrains.json`,
   * `.jetbrains.local.json` or `.jetbrains.env.json`. It resolves the path from the url, with no VFS access.
   */
  private fun configPath(storageContext: SettingsContext, locality: LayerLocality): Path {
    val directoryContext = requireNotNull(storageContext.realContext as? VirtualFileSettingsContext) {
      "storage context '${storageContext.identity}' is not a file context"
    }
    return directoryContext.nioPath.resolve(localityFileName(CONFIG_FILE, locality))
  }

  /** Holds the local path of this context, resolved from its url with no VFS access. */
  private val VirtualFileSettingsContext.nioPath: Path
    get() = Path.of(VirtualFileManager.extractPath(url))

  override suspend fun split(storageContext: SettingsContext, locality: LayerLocality, model: MapValue): List<Pair<SettingsContext, MapValue>> {
    val virtualFileSettingsContext = storageContext.realContext as? VirtualFileSettingsContext ?: return listOf(storageContext to model)
    val currentFileConfigs = currentFileConfigsMap[virtualFileSettingsContext.url to locality].orEmpty()
    val filesData = (model.values[PER_FILE_CONTAINER] as? MapValue)?.values ?: emptyMap()
    val layersData = ArrayList<Pair<SettingsContext, MapValue>>()
    // The directory layer is everything but the synthetic per-file container.
    layersData.add(storageContext to MapValue.wrapOwned(model.values - PER_FILE_CONTAINER))
    val newFileConfigs = LinkedHashMap<String, SettingsContext>()
    val project = virtualFileSettingsContext.project
    if (project != null && filesData.isNotEmpty()) {
      readAction {
        val directoryVirtualFile =
          VirtualFileManager.getInstance().findFileByUrl(virtualFileSettingsContext.url)?.takeIf { it.isValid && it.isDirectory }
          ?: return@readAction
        for ((fileName, unTypedFileData) in filesData) {
          val fileData = unTypedFileData as? MapValue ?: continue
          val fileVirtualFile = findConfiguredFile(directoryVirtualFile, fileName) ?: continue
          val fileContext = SettingsContext.file(fileVirtualFile, project)
          layersData.add(fileContext to fileData)
          newFileConfigs[fileName] = fileContext
        }
      }
    }
    // Clear entries emitted before but gone now, using the remembered context (the file may no longer be resolvable).
    for ((currentFileName, currentFileContext) in currentFileConfigs) {
      if (currentFileName !in newFileConfigs) layersData.add(currentFileContext to MapValue(emptyMap()))
    }
    currentFileConfigsMap[virtualFileSettingsContext.url to locality] = newFileConfigs
    return layersData
  }

  /**
   * Loads the directory configs that [scopeContext] covers. It suspends until the file name index is ready. See
   * [findConfigDirectories].
   */
  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> = loadConfigs(scopeContext)

  private suspend fun loadConfigs(scopeContext: SettingsContext): List<LayerData> {
    val dirContexts = findConfigDirectories().filter { it.coveredBy(scopeContext) }
    // Load each directory config concurrently and independently: one unreadable config must not sink the others.
    return collectIsolatedInParallel(dirContexts, { dir, e -> LOG.error("Failed to load config for '${dir.identity}'", e) }) { doLoad(it) }
  }

  /**
   * Returns the contexts of the directories in the project content that hold a config of any locality. It looks up
   * the [CONFIG_FILE_NAMES] in the [FilenameIndex] and walks no directory (ADR 0163). The content scope keeps out the
   * excluded directories and the libraries.
   *
   * It suspends until the project is in smart mode, so the index is ready. The wait runs in the [scope] of this
   * service, because the platform drops a wait for smart mode when the project closes and never resumes it. So a
   * close ends the wait, and the closed project gives no directories.
   */
  private suspend fun findConfigDirectories(): List<SettingsContext> {
    val lookup = scope.async {
      smartReadAction(project) {
        val configDirectories = LinkedHashSet<VirtualFile>()
        FilenameIndex.processFilesByNames(CONFIG_FILE_NAMES, true, ProjectScope.getContentScope(project), null) { file ->
          if (!file.isDirectory) file.parent?.let(configDirectories::add)
          true
        }
        configDirectories.map { SettingsContext.file(it, project) }
      }
    }
    try {
      lookup.join()
    }
    finally {
      lookup.cancel()
    }
    return if (lookup.isCancelled && !scope.isActive) emptyList() else lookup.await()
  }

  // Do not inline. BaseLayerService sits in the same package in another module, so another class loader, and a
  // lambda of this class is no subclass of it: it cannot reach a protected member across the two loaders. This
  // forwarder is reached as a private member of this class, and it calls `load` as a subclass may.
  private suspend fun doLoad(storageContext: SettingsContext): List<LayerData> = load(storageContext)

  /**
   * Returns whether the config of this directory loads for [scopeContext]. A directory, project or application scope
   * is a root, so the configs at it and under it load. A file has no config of its own. Its overrides live in the
   * config of its directory, an ancestor. So a file scope loads exactly that one directory.
   */
  private fun SettingsContext.coveredBy(scopeContext: SettingsContext): Boolean {
    val real = scopeContext.realContext
    return if (real is VirtualFileSettingsContext && Files.isRegularFile(real.nioPath)) identity == real.parentContext.identity
    else scopeContext.isAncestorOf(this, strict = false)
  }

  private fun findConfiguredFile(dir: VirtualFile, name: String): VirtualFile? =
    dir.findChild(name)?.takeIf { it.isValid && !it.isDirectory }

  // Do not inline, for the same reason as doLoad: the coroutine of ConfigFileListener cannot reach a protected member.
  private fun doRefresh(storageContext: SettingsContext) = refresh(storageContext)

  private inner class ConfigFileListener : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
      if (!watcherEnabled) return null
      // The directories whose config an event touched in any way. A refresh reads the current state again to decide.
      val dirs = LinkedHashSet<VirtualFile>()
      for (event in events) collect(event, dirs)
      if (dirs.isEmpty()) return null
      return object : AsyncFileListener.ChangeApplier {
        override fun afterVfsChange() {
          scope.launch {
            for (dir in dirs) {
              // A VFS event is application-wide, so this listener also sees the dirs of other projects. Keep only the dirs of this project.
              val context = readAction {
                if (!project.isDisposed && ProjectFileIndex.getInstance(project).isInContent(dir)) SettingsContext.file(dir, project) else null
              }
              if (context != null) doRefresh(context)
            }
          }
        }
      }
    }

    private fun collect(event: VFileEvent, dirs: MutableSet<VirtualFile>) {
      when (event) {
        is VFileCreateEvent -> if (isConfigName(event.childName)) dirs.add(event.parent)
        is VFileContentChangeEvent -> track(event.file, event.file.parent, dirs)
        is VFileDeleteEvent -> track(event.file, event.file.parent, dirs)
        is VFileCopyEvent -> if (isConfigName(event.newChildName)) dirs.add(event.newParent)
        is VFileMoveEvent -> if (isConfigName(event.file.name)) {
          dirs.add(event.oldParent)
          dirs.add(event.newParent)
        }
        is VFilePropertyChangeEvent -> if (event.propertyName == VirtualFile.PROP_NAME) {
          val dir = event.file.parent ?: return
          if (isConfigName(event.oldValue) || isConfigName(event.newValue)) dirs.add(dir)
        }
      }
    }

    private fun track(file: VirtualFile, dir: VirtualFile?, dirs: MutableSet<VirtualFile>) {
      if (dir != null && isConfigName(file.name)) dirs.add(dir)
    }
  }

  companion object {
    /** Holds the name of the team config file. [localityFileName] derives the names of the other localities from it. */
    const val CONFIG_FILE: String = "$JETBRAINS_CONFIG_NAME.json"

    /**
     * Holds the config file names of all the localities: `.jetbrains.json`, `.jetbrains.local.json` and
     * `.jetbrains.env.json`. The file layer finds the configs by them in the file name index, and the watcher reacts
     * to them.
     */
    val CONFIG_FILE_NAMES: Set<String> = LayerLocality.entries.mapTo(HashSet()) { localityFileName(CONFIG_FILE, it) }

    /**
     * Returns whether [name] is one of the [CONFIG_FILE_NAMES].
     *
     * @param name the file name from a VFS event, which can be absent or not a string
     */
    private fun isConfigName(name: Any?): Boolean = name is String && name in CONFIG_FILE_NAMES

    /**
     * Holds the top-level key of the synthetic per-file container in a directory config: `@@perfile`, then a file
     * name, then the overrides of that file. It is only an encoding on the disk. The service expands it into the
     * layers of the per-file contexts, so it never reaches the vault. The doubled `@@` sets it apart from the single
     * `@` marker of slotted storage ([SettingsKey.STORAGE_IDENTITY_PREFIX]). A real key cannot collide, because a key
     * name and a key prefix may not start with `@`.
     */
    const val PER_FILE_CONTAINER: String = "@@perfile"

    fun getInstance(project: Project): FileLayerService = project.service()

    private val LOG = logger<FileLayerService>()
  }
}
