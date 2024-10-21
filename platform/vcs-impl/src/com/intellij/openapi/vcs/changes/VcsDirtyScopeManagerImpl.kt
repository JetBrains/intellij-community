// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.HashingStrategy
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

private val LOG = logger<VcsDirtyScopeManagerImpl>()

@ApiStatus.Internal
class VcsDirtyScopeManagerImpl(private val project: Project) : VcsDirtyScopeManager(), Disposable {
  private var dirtBuilder = DirtBuilder()
  private var dirtInProgress: DirtBuilder? = null
  private var refreshInProgress: ActionCallback? = null

  private var isReady = false
  private val LOCK = Any()

  init {
    val busConnection = project.getMessageBus().connect()
    busConnection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun fileTypesChanged(event: FileTypeEvent) {
        // Listen changes in 'FileTypeManager.getIgnoredFilesList':
        //   'ProjectLevelVcsManager.getVcsFor' depends on it via 'ProjectLevelVcsManager.isIgnored',
        //   which might impact which files are visible in ChangeListManager.

        // Event does not allow listening for 'getIgnoredFilesList' changes directly, listen for all generic events instead.

        val isGenericEvent = event.addedFileType == null && event.removedFileType == null
        if (isGenericEvent) {
          ApplicationManager.getApplication().invokeLater({ markEverythingDirty() }, ModalityState.nonModal(), project.getDisposed())
        }
      }
    })

    if (`is`("ide.hide.excluded.files")) {
      busConnection.subscribe<ModuleRootListener>(ModuleRootListener.TOPIC, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          // 'ProjectLevelVcsManager.getVcsFor' depends on excluded roots via 'ProjectLevelVcsManager.isIgnored'
          ApplicationManager.getApplication().invokeLater({ markEverythingDirty() }, ModalityState.nonModal(), project.getDisposed())
        }
      })
      //busConnection.subscribe(AdditionalLibraryRootsListener.TOPIC, ((presentableLibraryName, oldRoots, newRoots, libraryNameForDebug) -> {
      //  ApplicationManager.getApplication().invokeLater(() -> markEverythingDirty(), ModalityState.NON_MODAL, myProject.getDisposed());
      //}));
    }
  }

  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): VcsDirtyScopeManagerImpl {
      return (getInstance(project) as VcsDirtyScopeManagerImpl)
    }

    @JvmStatic
    fun getDirtyScopeHashingStrategy(vcs: AbstractVcs): HashingStrategy<FilePath>? {
      return if (vcs.needsCaseSensitiveDirtyScope()) ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY else null
    }
  }

  private fun startListenForChanges() {
    val ready = !project.isDisposed() && project.isOpen()
    synchronized(LOCK) {
      isReady = ready
    }
    if (ready) {
      project.getService<VcsDirtyScopeVfsListener?>(VcsDirtyScopeVfsListener::class.java)
      markEverythingDirty()
    }
  }

  override fun markEverythingDirty() {
    if ((!project.isOpen()) || project.isDisposed() || ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().isEmpty()) {
      return
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("everything dirty: " + findFirstInterestingCallerClass())
    }

    val wasReady: Boolean
    val ongoingRefresh: ActionCallback?
    synchronized(LOCK) {
      wasReady = isReady
      if (wasReady) {
        dirtBuilder.markEverythingDirty()
      }
      ongoingRefresh = refreshInProgress
    }

    if (wasReady) {
      ChangeListManagerImpl.getInstanceImpl(project).scheduleUpdateImpl()
      ongoingRefresh?.setRejected()
    }
  }

  override fun dispose() {
    synchronized(LOCK) {
      isReady = false
      dirtBuilder = DirtBuilder()
      dirtInProgress = null
      refreshInProgress = null
    }
  }

  private fun groupByVcs(from: Sequence<FilePath>?): Map<VcsRoot, Set<FilePath>> {
    if (from == null) {
      return emptyMap()
    }

    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val map = HashMap<VcsRoot, MutableSet<FilePath>>()
    for (path in from) {
      val vcsRoot = vcsManager.getVcsRootObjectFor(path) ?: continue
      if (vcsRoot.vcs != null) {
        val pathSet = map.computeIfAbsent(vcsRoot) { key ->
          val strategy = getDirtyScopeHashingStrategy(key.vcs!!)
          if (strategy == null) HashSet() else CollectionFactory.createCustomHashingStrategySet(strategy)
        }
        pathSet.add(path)
      }
    }
    return map
  }

  private fun groupFilesByVcs(from: Collection<VirtualFile>?): Map<VcsRoot, Set<FilePath>> {
    if (from == null) {
      return emptyMap()
    }
    return groupByVcs(from.asSequence().map { VcsUtil.getFilePath(it) })
  }

  fun fileVcsPathsDirty(
    filesConverted: Map<VcsRoot, Set<FilePath>>,
    dirsConverted: Map<VcsRoot, Set<FilePath>>
  ) {
    if (filesConverted.isEmpty() && dirsConverted.isEmpty()) {
      return
    }

    LOG.debug { "dirty files: ${toString(filesConverted)}; dirty dirs: ${toString(dirsConverted)}; ${findFirstInterestingCallerClass()}" }

    var hasSomethingDirty = false
    for (vcsRoot in ContainerUtil.union<VcsRoot>(filesConverted.keys, dirsConverted.keys)) {
      val files = filesConverted.get(vcsRoot) ?: emptySet()
      val dirs = dirsConverted.get(vcsRoot) ?: emptySet()

      synchronized(LOCK) {
        if (isReady) {
          hasSomethingDirty = hasSomethingDirty or dirtBuilder.addDirtyFiles(vcsRoot = vcsRoot, files = files, dirs = dirs)
        }
      }
    }

    if (hasSomethingDirty) {
      ChangeListManagerImpl.getInstanceImpl(project).scheduleUpdateImpl()
    }
  }

  override fun filePathsDirty(
    filesDirty: Collection<FilePath>?,
    dirsRecursivelyDirty: Collection<FilePath>?
  ) {
    try {
      fileVcsPathsDirty(groupByVcs(filesDirty?.asSequence()), groupByVcs(dirsRecursivelyDirty?.asSequence()))
    }
    catch (_: CancellationException) {
    }
  }

  override fun filesDirty(
    filesDirty: Collection<VirtualFile>?,
    dirsRecursivelyDirty: Collection<VirtualFile>?
  ) {
    try {
      fileVcsPathsDirty(groupFilesByVcs(filesDirty), groupFilesByVcs(dirsRecursivelyDirty))
    }
    catch (_: CancellationException) {
    }
  }

  override fun fileDirty(file: VirtualFile) {
    fileDirty(VcsUtil.getFilePath(file))
  }

  override fun fileDirty(file: FilePath) {
    filePathsDirty(filesDirty = setOf(file), dirsRecursivelyDirty = null)
  }

  override fun dirDirtyRecursively(dir: VirtualFile) {
    dirDirtyRecursively(VcsUtil.getFilePath(dir))
  }

  override fun dirDirtyRecursively(path: FilePath) {
    filePathsDirty(filesDirty = null, dirsRecursivelyDirty = setOf(path))
  }

  /**
   * Take the current dirty scope into processing.
   * Should call [.changesProcessed] when done to notify [.whatFilesDirty] that scope is no longer dirty.
   */
  fun retrieveScopes(): VcsInvalidated? {
    val callback = ActionCallback()
    val dirtBuilder: DirtBuilder?
    synchronized(LOCK) {
      if (!isReady) return null
      LOG.assertTrue(dirtInProgress == null)

      dirtBuilder = this@VcsDirtyScopeManagerImpl.dirtBuilder
      dirtInProgress = dirtBuilder
      this@VcsDirtyScopeManagerImpl.dirtBuilder = DirtBuilder()
      refreshInProgress = callback
    }
    return calculateInvalidated(dirtBuilder!!, callback)
  }

  fun hasDirtyScopes(): Boolean {
    synchronized(LOCK) {
      if (!isReady) return false
      LOG.assertTrue(dirtInProgress == null)
      return !dirtBuilder.isEmpty()
    }
  }

  fun changesProcessed() {
    synchronized(LOCK) {
      dirtInProgress = null
      refreshInProgress = null
    }
  }

  private fun calculateInvalidated(dirt: DirtBuilder, callback: ActionCallback): VcsInvalidated {
    return VcsInvalidated(scopes = dirt.buildScopes(project), isEverythingDirty = dirt.isEverythingDirty, callback = callback)
  }

  override fun whatFilesDirty(files: MutableCollection<out FilePath>): Collection<FilePath> {
    return ApplicationManager.getApplication().runReadAction(ThrowableComputable {
      val result = ArrayList<FilePath>()
      synchronized(LOCK) {
        if (!isReady) {
          return@ThrowableComputable emptyList()
        }
        for (fp in files) {
          if (dirtBuilder.isFileDirty(fp) || dirtInProgress != null && dirtInProgress!!.isFileDirty(fp)) {
            result.add(fp)
          }
        }
      }
      result
    })
  }

  internal class MyStartupActivity : VcsStartupActivity {
    override suspend fun execute(project: Project) {
      getInstanceImpl(project).startListenForChanges()
    }

    override val order: Int
      get() = VcsInitObject.DIRTY_SCOPE_MANAGER.order
  }
}

private fun toString(filesByVcs: Map<VcsRoot, Set<FilePath>>): String {
  return filesByVcs.keys.joinToString("\n") { vcs ->
    vcs.vcs.toString() + ": " + filesByVcs.getValue(vcs).joinToString("\n") { it.getPath() }
  }
}

private fun findFirstInterestingCallerClass(): Class<*>? {
  for (i in 1..7) {
    val clazz = ReflectionUtil.findCallerClass(i)
    if (clazz == null || !clazz.getName().contains(VcsDirtyScopeManagerImpl::class.java.getName())) {
      return clazz
    }
  }
  return null
}