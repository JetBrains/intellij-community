// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.VfsUtil.findFileByUrlIfCached
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.TransientVirtualFileVfsRefreshUtils.createTransientChild
import com.intellij.openapi.vfs.newvfs.events.ChildInfo
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.core.fileIndex.impl.TraverseState
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.Collections

/**
 * Filters global VFS events for a project before the change.
 * [getAfterEvents] constructs the selected events with real files after the change.
 *
 * @see ProjectBulkFileListener
 */
@ApiStatus.Internal
@VisibleForTesting
class ProjectVfsEventBatch @RequiresReadLock constructor(private val project: Project, events: List<VFileEvent>) {
  private val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
  private val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager() as VirtualFileUrlManagerImpl

  private val eventTemplates = ArrayList<EventTemplate>()

  init {
    prepare(events)
  }

  @RequiresWriteLock
  fun getAfterEvents(): List<VFileEvent> {
    val output = ArrayList<VFileEvent>(eventTemplates.size)
    for (template in eventTemplates) {
      template.appendTo(output)
    }
    return Collections.unmodifiableList(output)
  }

  /**
   * VFS events require the file or its parent to be cached in VFS.
   * Some events for newly created descendants can only be constructed after VFS is updated.
   */
  private interface EventTemplate {
    fun appendTo(output: MutableList<VFileEvent>)
  }

  private class OriginalEvent(val event: VFileEvent) : EventTemplate {
    override fun appendTo(output: MutableList<VFileEvent>) {
      output.add(event)
    }
  }

  private class CreateEvent(val requestor: Any?, val file: VirtualFile) : EventTemplate {
    override fun appendTo(output: MutableList<VFileEvent>) {
      val parent = file.parent?.validOrNull() ?: return
      if (!file.isValid) return
      output.add(VFileCreateEvent(requestor, parent, file.name, file.isDirectory, null, null, null))
    }
  }

  private class CreatedTree(val event: VFileEvent, val included: Boolean, val childEvents: List<ChildEvent>) : EventTemplate {
    override fun appendTo(output: MutableList<VFileEvent>) {
      val createdFile = when (event) {
        is VFileCreateEvent -> event.findCreatedFileWithCacheIfCached()
        is VFileCopyEvent -> event.findCreatedFileWithCacheIfCached()
        else -> error("Expected a create or copy event")
      } ?: return
      if (included) {
        output.add(event)
      }
      if (!createdFile.isDirectory) return
      val realParent = createdFile as? NewVirtualFile ?: return
      val sourceParent = (event as? VFileCopyEvent)?.file as? NewVirtualFile
      if (event is VFileCopyEvent && sourceParent?.isDirectory != true) return
      for (childEvent in childEvents) {
        childEvent.appendTo(event.requestor, realParent, sourceParent, output)
      }
    }
  }

  /**
   * Describes a child from [ChildInfo] in a create or copy event.
   * Constructs the event in a write action, when the parent exists in VFS.
   */
  private class ChildEvent(val info: ChildInfo, val included: Boolean, val children: List<ChildEvent>) {
    fun appendTo(requestor: Any?, realParent: NewVirtualFile, sourceParent: NewVirtualFile?, output: MutableList<VFileEvent>) {
      val name = info.name.toString()
      val realChild = realParent.findChildIfCached(name)?.validOrNull() ?: return
      val sourceChild = if (sourceParent != null) {
        sourceParent.findChildIfCached(name)?.validOrNull() ?: return
      }
      else {
        null
      }
      if (included) {
        val event = if (sourceChild != null) {
          VFileCopyEvent(requestor, sourceChild, realParent, name, info.fileAttributes,
                         info.symlinkTarget, info.children, info.isAllChildren)
        }
        else {
          VFileCreateEvent(requestor, realParent, name, info.fileAttributes?.isDirectory ?: realChild.isDirectory,
                           info.fileAttributes, info.symlinkTarget, info.children, info.isAllChildren)
        }

        output.add(event)
      }
      for (child in children) {
        child.appendTo(requestor, realChild, sourceChild, output)
      }
    }
  }

  private fun prepare(events: List<VFileEvent>) {
    for (event in events) {
      ProgressManager.checkCanceled()
      when (event) {
        is VFileDeleteEvent -> {
          if (workspaceFileIndex.isInWorkspace(event.file)) {
            eventTemplates.add(OriginalEvent(event))
          }
          appendCachedDeletes(event.requestor, event.file, initialTraverseState(event.file))
        }
        is VFileCreateEvent -> prepareCreateOrCopy(event, event.parent, event.childName, event.children)
        is VFileCopyEvent -> prepareCreateOrCopy(event, event.newParent, event.newChildName, event.children)
        is VFileMoveEvent -> prepareMoveOrRename(event)
        is VFilePropertyChangeEvent -> when {
          event.isRename -> prepareMoveOrRename(event)
          workspaceFileIndex.isInWorkspace(event.file) -> eventTemplates.add(OriginalEvent(event))
        }
        else -> {
          if (event.file?.let { workspaceFileIndex.isInWorkspace(it) } == true) {
            eventTemplates.add(OriginalEvent(event))
          }
        }
      }
    }
  }

  /**
   * - Within the project, keep the move or rename event and emit create events for cached children.
   * - Out of the project, emit delete events for the file and its cached children.
   * - Into the project, emit create events for the file and its cached children.
   */
  private fun prepareMoveOrRename(event: VFileEvent) {
    val file = event.file
    checkNotNull(file) { "File must be not null. event: $event" }
    val wasInWorkspace = workspaceFileIndex.isInWorkspace(file)
    val futureFile = when (event) {
      is VFileMoveEvent -> createTransientChild(event.newParent, file.name)
      else -> createTransientChild(file.parent, (event as VFilePropertyChangeEvent).newValue as String)
    }
    val state = initialTraverseState(futureFile.parent)?.goDown(futureFile)
    val willBeInWorkspace = state?.included == true
    when {
      wasInWorkspace && willBeInWorkspace -> {
        eventTemplates.add(OriginalEvent(event))
        appendCreatesForCachedChildren(event, file, futureFile, state)
      }
      wasInWorkspace -> {
        eventTemplates.add(OriginalEvent(VFileDeleteEvent(event.requestor, file)))
        appendCachedDeletes(event.requestor, file, initialTraverseState(file))
      }
      willBeInWorkspace -> {
        eventTemplates.add(CreateEvent(event.requestor, file))
        appendCreatesForCachedChildren(event, file, futureFile, state)
      }
      else -> {
        appendCachedDeletes(event.requestor, file, initialTraverseState(file))
        appendCreatesForCachedChildren(event, file, futureFile, state)
      }
    }
  }

  /** Traverses the cached children of [file] and prepares create events for them. */
  private fun appendCreatesForCachedChildren(event: VFileEvent, file: VirtualFile, futureParent: VirtualFile, state: State?) {
    if (state == null || file !is NewVirtualFile || !file.isDirectory) return
    if (!state.includesChildren && virtualFileUrlManager.findNode(futureParent.url) == null) return
    for (child in file.cachedChildren) {
      ProgressManager.checkCanceled()
      if (!child.isValid) continue
      val futureChild = createTransientChild(futureParent, child.name)
      val childState = state.goDown(futureChild) ?: continue
      if (childState.included) {
        eventTemplates.add(CreateEvent(event.requestor, child))
      }
      appendCreatesForCachedChildren(event, child, futureChild, childState)
    }
  }

  /** Traverses the cached children of [file] and constructs delete events for them. */
  private fun appendCachedDeletes(requestor: Any?, file: VirtualFile, state: State?) {
    if (state == null || file !is NewVirtualFile || !file.isDirectory) return
    if (!state.includesChildren) {
      virtualFileUrlManager.processChildrenRecursively(file.url) { childUrl ->
        ProgressManager.checkCanceled()
        if (workspaceFileIndex.getFileSetsAt(childUrl).mask == 0) {
          return@processChildrenRecursively TreeNodeProcessingResult.CONTINUE
        }
        val child = findFileByUrlIfCached(childUrl.url)
          ?: return@processChildrenRecursively TreeNodeProcessingResult.SKIP_CHILDREN
        val path = ArrayList<VirtualFile>()
        var current = child
        while (current != file) {
          path.add(current)
          current = current.parent ?: return@processChildrenRecursively TreeNodeProcessingResult.SKIP_CHILDREN
        }
        var childState: State = state
        for (descendant in path.asReversed()) {
          childState = childState.goDown(descendant)
            ?: return@processChildrenRecursively TreeNodeProcessingResult.SKIP_CHILDREN
        }
        if (childState.included) {
          eventTemplates.add(OriginalEvent(VFileDeleteEvent(requestor, child)))
        }
        appendCachedDeletes(requestor, child, childState)
        TreeNodeProcessingResult.SKIP_CHILDREN
      }
      return
    }
    for (child in file.cachedChildren) {
      ProgressManager.checkCanceled()
      if (!child.isValid) continue
      val childState = state.goDown(child) ?: continue
      if (childState.included) {
        eventTemplates.add(OriginalEvent(VFileDeleteEvent(requestor, child)))
      }
      appendCachedDeletes(requestor, child, childState)
    }
  }

  private fun prepareCreateOrCopy(event: VFileEvent, parent: VirtualFile, name: String, children: Array<ChildInfo>?) {
    val parentState = initialTraverseState(parent) ?: return
    val child = createTransientChild(parent, name)
    val childState = parentState.goDown(child) ?: return

    val childEvents = children?.mapNotNullTo(ArrayList(children.size)) { childInfo ->
      prepareChildEvent(child, childState, childInfo)
    }.orEmpty()

    if (childState.included || childEvents.isNotEmpty()) {
      eventTemplates.add(CreatedTree(event, childState.included, childEvents))
    }
  }

  private fun prepareChildEvent(parent: /*Transient*/VirtualFile, state: State, info: ChildInfo): ChildEvent? {
    ProgressManager.checkCanceled()
    val child = createTransientChild(parent, info.name.toString())
    val childState = state.goDown(child) ?: return null

    val descendants = info.children?.mapNotNullTo(ArrayList(info.children!!.size)) { childInfo ->
      prepareChildEvent(child, childState, childInfo)
    }.orEmpty()

    if (!childState.included && descendants.isEmpty()) {
      return null
    }
    return ChildEvent(info, childState.included, descendants)
  }

  private fun initialTraverseState(file: VirtualFile): State? {
    val traverseState = TraverseState.getTraverseState(file, project) ?: return null
    return State(traverseState, virtualFileUrlManager.findNode(file.url))
  }

  private inner class State(private val traverseState: TraverseState, private val url: VirtualFileUrl?) {
    val included: Boolean get() = traverseState.isInWorkspace
    val includesChildren: Boolean get() = traverseState.maskRecursive != 0

    fun goDown(file: VirtualFile): State? {
      val childUrl = url?.findChild(file.name)
      val childState = traverseState.goDown(file, childUrl, workspaceFileIndex) ?: return null
      return State(childState, childUrl)
    }
  }
}

private fun createTransientChild(parent: VirtualFile, name: String): VirtualFile =
  createTransientChild(parent, name, parent.fileSystem as NewVirtualFileSystem)

private fun VFileCreateEvent.findCreatedFileWithCacheIfCached(): VirtualFile? {
  val parent = this.parent.validOrNull() ?: return null
  val file = when (parent) {
    is NewVirtualFile -> parent.findChildIfCached(this.childName)
    else -> this.file
  }
  return file?.validOrNull()
}

private fun VFileCopyEvent.findCreatedFileWithCacheIfCached(): VirtualFile? {
  val parent = this.newParent.validOrNull() ?: return null
  val file = when (parent) {
    is NewVirtualFile -> parent.findChildIfCached(this.newChildName)
    else -> this.findCreatedFile()
  }
  return file?.validOrNull()
}

private fun <T : VirtualFile> T.validOrNull(): T? = if (this.isValid) this else null
