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
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FilePointerProviderImpl(project: Project) : FilePointerProvider, Disposable {
  private val sourceRootPointers = mutableMapOf<VirtualFileUrl, Pair<VirtualFilePointer, Disposable>>()
  private val contentRootPointers = mutableMapOf<VirtualFileUrl, Pair<VirtualFilePointer, Disposable>>()
  private val excludedRootsPointers = mutableMapOf<VirtualFileUrl, Pair<VirtualFilePointer, Disposable>>()
  private val modulePointers = mutableMapOf<String, HashMap<VirtualFileUrl, Pair<VirtualFilePointer, Disposable>>>()

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
              val removedSourceRoot = sourceRootPointers.remove(it)
              removedSourceRoot?.second?.let { disposable ->  Disposer.dispose(disposable) }

              val removedContentRoot = contentRootPointers.remove(it)
              removedContentRoot?.second?.let { disposable ->  Disposer.dispose(disposable) }

              val removedExcludedRoot = excludedRootsPointers.remove(it)
              removedExcludedRoot?.second?.let { disposable ->  Disposer.dispose(disposable) }

              modulePointers.entries.removeIf { (_, value) ->
                val removed = value.remove(it)
                removed?.second?.let { disposable -> Disposer.dispose(disposable) }
                return@removeIf value.isEmpty()
              }
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
      override fun changed(event: VersionedStorageChange) {
        synchronized(this@FilePointerProviderImpl) {
          event.getAllChanges().filterIsInstance<EntityChange.Removed<*>>().forEach { change ->
            val entity = change.entity
            when (entity) {
              is SourceRootEntity -> {
                val pointer = sourceRootPointers[entity.url]
                if (pointer != null) {
                  sourceRootPointers.remove(entity.url)
                  Disposer.dispose(pointer.second)
                }
              }
              is ContentRootEntity -> {
                val pointer = contentRootPointers[entity.url]
                if (pointer != null) {
                  contentRootPointers.remove(entity.url)
                  Disposer.dispose(pointer.second)
                }

                entity.excludedUrls.forEach {
                  val excludedUrl = excludedRootsPointers[it]
                  if (excludedUrl != null) {
                    excludedRootsPointers.remove(it)
                    Disposer.dispose(excludedUrl.second)
                  }
                }
              }
              is ModuleEntity -> {
                val removedModuleRoots = modulePointers[entity.name]
                if (removedModuleRoots != null) {
                  removedModuleRoots.values.forEach { Disposer.dispose(it.second) }
                  modulePointers.remove(entity.name)
                }
              }
            }
          }
        }
      }
    })
  }

  override fun dispose() = Unit

  @Synchronized
  override fun getAndCacheSourceRoot(url: VirtualFileUrl): VirtualFilePointer {
    return sourceRootPointers.getOrPut(url) {
      val disposable = nextDisposable()
      Pair(VirtualFilePointerManager.getInstance().create(url.url, disposable, null), disposable)
    }.first
  }

  @Synchronized
  override fun getAndCacheContentRoot(url: VirtualFileUrl): VirtualFilePointer {
    return contentRootPointers.getOrPut(url) {
      val disposable = nextDisposable()
      Pair(VirtualFilePointerManager.getInstance().create(url.url, disposable, null), disposable)
    }.first
  }

  @Synchronized
  override fun getAndCacheExcludedRoot(url: VirtualFileUrl): VirtualFilePointer {
    return excludedRootsPointers.getOrPut(url) {
      val disposable = nextDisposable()
      Pair(VirtualFilePointerManager.getInstance().create(url.url, disposable, null), disposable)
    }.first
  }

  @Synchronized
  override fun getAndCacheModuleRoot(name: String, url: VirtualFileUrl): VirtualFilePointer {
    val map = modulePointers.getOrPut(name) { HashMap() }
    return map.getOrPut(url) {
      val disposable = nextDisposable()
      Pair(VirtualFilePointerManager.getInstance().create(url.url, disposable, null), disposable)
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
    sourceRootPointers.forEach { (_, v) -> Disposer.dispose(v.second) }
    contentRootPointers.forEach { (_, v) -> Disposer.dispose(v.second) }
    excludedRootsPointers.forEach { (_, v) -> Disposer.dispose(v.second) }
    modulePointers.forEach { (_, v) ->
      v.forEach { (_, o) -> Disposer.dispose(o.second) }
    }
    fileContainers.map { it.value.second }.forEach { Disposer.dispose(it) }

    sourceRootPointers.clear()
    contentRootPointers.clear()
    excludedRootsPointers.clear()
    modulePointers.clear()
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
  internal fun getContentRootPointers() = contentRootPointers

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