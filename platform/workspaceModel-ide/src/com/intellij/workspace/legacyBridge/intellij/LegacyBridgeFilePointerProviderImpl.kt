// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
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
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.VirtualFileUrlManager
import com.intellij.workspace.ide.getInstance
import com.intellij.workspace.toVirtualFileUrl
import org.jdom.Element
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LegacyBridgeFilePointerProviderImpl(project: Project) : LegacyBridgeFilePointerProvider, Disposable {
  private val filePointers = mutableMapOf<VirtualFileUrl, VirtualFilePointer>()
  private val filePointerDisposables = mutableListOf<Disposable>()

  private val fileContainers = mutableMapOf<LegacyBridgeFileContainer, VirtualFilePointerContainer>()
  private val fileContainerDisposables = mutableListOf<Disposable>()

  private val fileContainerUrlsLock = ReentrantLock()
  private val fileContainerUrls = MultiMap.create<VirtualFileUrl, LegacyBridgeFileContainer>()
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener(AsyncFileListener { events ->
      val pointersToProcess = mutableSetOf<VirtualFileUrl>()
      val containersToProcess = mutableSetOf<LegacyBridgeFileContainer>()

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
          synchronized(this@LegacyBridgeFilePointerProviderImpl) {
            pointersToProcess.forEach { filePointers.remove(it) }
            containersToProcess.forEach { fileContainers.remove(it) }
          }
        }
      }
    }, this)
  }

  override fun dispose() = Unit

  @Synchronized
  override fun getAndCacheFilePointer(url: VirtualFileUrl): VirtualFilePointer {
    return filePointers.getOrPut(url) {
      val disposable = nextDisposable()
      filePointerDisposables.add(disposable)
      VirtualFilePointerManager.getInstance().create(url.url, disposable, null)
    }
  }

  @Synchronized
  override fun getAndCacheFileContainer(description: LegacyBridgeFileContainer): VirtualFilePointerContainer {
    val existingContainer = fileContainers[description]
    if (existingContainer != null) return existingContainer

    val disposable = nextDisposable()
    fileContainerDisposables.add(disposable)
    val container = VirtualFilePointerManager.getInstance().createContainer(disposable, object : VirtualFilePointerListener {
      override fun validityChanged(pointers: Array<out VirtualFilePointer>) {  }
    })

    for (url in description.urls) {
      container.add(url.url)
    }
    for (jarDirectory in description.jarDirectories) {
      container.addJarDirectory(jarDirectory.directoryUrl.url, jarDirectory.recursive)
    }

    registerContainer(description, container)

    return ReadonlyFilePointerContainer(container)
  }

  @Synchronized
  fun disposeAndClearCaches() {
    filePointerDisposables.forEach { Disposer.dispose(it) }
    filePointerDisposables.clear()
    fileContainerDisposables.forEach { Disposer.dispose(it) }
    fileContainerDisposables.clear()

    fileContainerUrlsLock.withLock { fileContainerUrls.clear() }
    filePointers.clear()
    fileContainers.clear()
  }

  private fun registerContainer(description: LegacyBridgeFileContainer, container: VirtualFilePointerContainer) {
    fileContainers[description] = container

    fileContainerUrlsLock.withLock {
      description.urls.forEach { fileContainerUrls.putValue(it, description) }
      description.jarDirectories.forEach { fileContainerUrls.putValue(it.directoryUrl, description) }
    }
  }

  private fun nextDisposable() = Disposer.newDisposable().also {
    Disposer.register(this, it)
  }

  private class ReadonlyFilePointerContainer(val container: VirtualFilePointerContainer) : VirtualFilePointerContainer {
    private fun throwReadonly(): Nothing = throw NotImplementedError("Read-Only File Pointer Container")

    override fun getFiles(): Array<VirtualFile> = container.files
    override fun getJarDirectories(): MutableList<Pair<String, Boolean>> = container.jarDirectories
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