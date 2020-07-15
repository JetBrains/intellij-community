// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FilePointerProviderImpl(project: Project) : FilePointerProvider, Disposable {
  private val filePointers = mutableMapOf<VirtualFileUrl, Triple<VirtualFilePointer, Disposable, FilePointerScope>>()

  private val fileContainers = mutableMapOf<FileContainerDescription, Pair<VirtualFilePointerContainer, Disposable>>()

  private val fileContainerUrlsLock = ReentrantLock()
  private val fileContainerUrls = MultiMap.create<VirtualFileUrl, FileContainerDescription>()
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      val pointersToProcess = mutableSetOf<VirtualFileUrl>()
      val containersToProcess = mutableSetOf<FileContainerDescription>()

      fileContainerUrlsLock.withLock {
        fun handleUrl(url: VirtualFileUrl) {
          pointersToProcess.add(url)
          for (descriptor in fileContainerUrls.get(url)) {
            containersToProcess.add(descriptor)
          }
        }

        for (event in events) {
          if (event is VFileMoveEvent) {
            handleUrl(event.file.toVirtualFileUrl(virtualFileManager))
          }
          else if (event is VFilePropertyChangeEvent && event.isRename) {
            handleUrl(event.file.toVirtualFileUrl(virtualFileManager))
          }
        }
      }

      object : AsyncFileListener.ChangeApplier {
        override fun beforeVfsChange() {
          synchronized(this@FilePointerProviderImpl) {
            pointersToProcess.forEach {
              val removed = filePointers.remove(it)
              removed?.second?.let { disposable -> Disposer.dispose(disposable) }
            }
            containersToProcess.forEach {
              val removed = fileContainers.remove(it)
              removed?.second?.let { disposable -> Disposer.dispose(disposable) }
            }
          }
        }
      }
    }, this)

    WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(), object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChanged) {
        synchronized(this@FilePointerProviderImpl) {
          event.getAllChanges().filterIsInstance<EntityChange.Removed<*>>().forEach { change ->
            val toRemove = ArrayList<Pair<VirtualFileUrl, Disposable>>()
            filePointers.forEach { (key, value) ->
              if (value.third.checkUrl(change.entity, key)) {
                toRemove += key to value.second
              }
            }
            toRemove.forEach {
              Disposer.dispose(it.second)
              filePointers.remove(it.first)
            }
          }
        }
      }
    })
  }

  override fun dispose() = Unit

  @Synchronized
  override fun getAndCacheFilePointer(url: VirtualFileUrl, scope: FilePointerScope): VirtualFilePointer {
    return filePointers.getOrPut(url) {
      val disposable = nextDisposable()
      Triple(VirtualFilePointerManager.getInstance().create(url.url, disposable, null), disposable, scope)
    }.first
  }

  @Synchronized
  override fun getAndCacheFileContainer(description: FileContainerDescription, scope: Disposable): VirtualFilePointerContainer {
    val existingContainer = fileContainers[description]
    if (existingContainer != null) return existingContainer.first

    val disposable = object : Disposable {
      override fun dispose() {
        unregisterContainer(description, this)
      }
    }
    Disposer.register(scope, disposable)
    val container = VirtualFilePointerManager.getInstance().createContainer(disposable, object : VirtualFilePointerListener {
      override fun validityChanged(pointers: Array<out VirtualFilePointer>) {}
    })

    for (url in description.urls) {
      container.add(url.url)
    }
    for (jarDirectory in description.jarDirectories) {
      container.addJarDirectory(jarDirectory.directoryUrl.url, jarDirectory.recursive)
    }

    registerContainer(description, container, disposable)

    return ReadonlyFilePointerContainer(container)
  }

  @Synchronized
  private fun unregisterContainer(description: FileContainerDescription, disposable: Disposable) {
    val entry = fileContainers[description]
    if (disposable != entry?.second) return
    fileContainers.remove(description)
    fileContainerUrlsLock.withLock {
      description.urls.forEach { fileContainerUrls.remove(it, description) }
      description.jarDirectories.forEach { fileContainerUrls.remove(it.directoryUrl, description) }
    }
  }

  @Synchronized
  fun clearCaches() {

    fileContainerUrlsLock.withLock { fileContainerUrls.clear() }
    filePointers.forEach { (_, v) -> Disposer.dispose(v.second) }
    fileContainers.map { it.value.second }.forEach { Disposer.dispose(it) }

    filePointers.clear()
    fileContainers.clear()
  }

  private fun registerContainer(description: FileContainerDescription,
                                container: VirtualFilePointerContainer,
                                disposable: Disposable) {
    fileContainers[description] = container to disposable

    fileContainerUrlsLock.withLock {
      description.urls.forEach { fileContainerUrls.putValue(it, description) }
      description.jarDirectories.forEach { fileContainerUrls.putValue(it.directoryUrl, description) }
    }
  }

  private fun nextDisposable() = Disposer.newDisposable().also {
    Disposer.register(this, it)
  }

  @TestOnly
  internal fun getFilePointers() = filePointers

  private class ReadonlyFilePointerContainer(val container: VirtualFilePointerContainer) : VirtualFilePointerContainer {
    private fun throwReadonly(): Nothing = throw NotImplementedError("Read-Only File Pointer Container")

    override fun getFiles(): Array<VirtualFile> = container.files
    override fun getJarDirectories(): MutableList<com.intellij.openapi.util.Pair<String, Boolean>> = container.jarDirectories
    override fun getUrls(): Array<String> = container.urls
    override fun getDirectories(): Array<VirtualFile> = container.directories
    override fun getList(): MutableList<VirtualFilePointer> = container.list

    override fun clone(parent: Disposable): VirtualFilePointerContainer = container.clone(parent)
    override fun clone(parent: Disposable, listener: VirtualFilePointerListener?): VirtualFilePointerContainer =
      container.clone(parent, listener)

    override fun findByUrl(url: String): VirtualFilePointer? = container.findByUrl(url)

    override fun writeExternal(element: Element, childElementName: String, externalizeJarDirectories: Boolean) {
      container.writeExternal(element, childElementName, externalizeJarDirectories)
    }

    override fun size(): Int = container.size()

    override fun clear() = throwReadonly()
    override fun addAll(that: VirtualFilePointerContainer) = throwReadonly()
    override fun addJarDirectory(directoryUrl: String, recursively: Boolean) = throwReadonly()
    override fun remove(pointer: VirtualFilePointer) = throwReadonly()
    override fun removeJarDirectory(directoryUrl: String): Boolean = throwReadonly()
    override fun moveUp(url: String) = throwReadonly()
    override fun add(file: VirtualFile) = throwReadonly()
    override fun add(url: String) = throwReadonly()
    override fun readExternal(rootChild: Element, childElementName: String, externalizeJarDirectories: Boolean) =
      throwReadonly()

    override fun killAll() = throwReadonly()
    override fun moveDown(url: String) = throwReadonly()
    override fun isEmpty(): Boolean = container.isEmpty
  }
}