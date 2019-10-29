package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.virtualFileUrl
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import org.jdom.Element
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LegacyBridgeFilePointerProviderImpl : LegacyBridgeFilePointerProvider, Disposable {
  private val filePointers: ConcurrentMap<VirtualFileUrl, VirtualFilePointer> = ContainerUtil.newConcurrentMap()

  private val fileContainers: ConcurrentMap<LegacyBridgeFileContainer, VirtualFilePointerContainer> = ContainerUtil.newConcurrentMap()

  private val fileContainerUrlsLock = ReentrantLock()
  private val fileContainerUrls = MultiMap.create<VirtualFileUrl, LegacyBridgeFileContainer>()

  init {
    VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
      override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
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
              handleUrl(event.file.virtualFileUrl)
            }
            else if (event is VFilePropertyChangeEvent && event.isRename) {
              handleUrl(event.file.virtualFileUrl)
            }
          }
        }

        return object : AsyncFileListener.ChangeApplier {
          override fun beforeVfsChange() {
            pointersToProcess.forEach { filePointers.remove(it) }
            containersToProcess.forEach { fileContainers.remove(it) }
          }
        }
      }
    }, this)
  }

  override fun dispose() = Unit

  override fun getAndCacheFilePointer(url: VirtualFileUrl): VirtualFilePointer =
    filePointers.getOrPut(url) {
      VirtualFilePointerManager.getInstance().create(url.url, this, object : VirtualFilePointerListener {
        override fun validityChanged(pointers: Array<out VirtualFilePointer>) {
          filePointers.remove(url)
        }
      })
    }

  override fun getAndCacheFileContainer(description: LegacyBridgeFileContainer): VirtualFilePointerContainer {
    val existingContainer = fileContainers[description]
    if (existingContainer != null) return existingContainer

    val container = VirtualFilePointerManager.getInstance().createContainer(
      this,
      object : VirtualFilePointerListener {
        override fun validityChanged(pointers: Array<out VirtualFilePointer>) {
          fileContainers.remove(description)
        }
      }
    )

    for (url in description.urls) {
      container.add(url.url)
    }
    for (jarDirectory in description.jarDirectories) {
      container.addJarDirectory(jarDirectory.directoryUrl.url, jarDirectory.recursive)
    }

    registerContainer(description, container)

    return ReadonlyFilePointerContainer(container)
  }

  private fun registerContainer(description: LegacyBridgeFileContainer, container: VirtualFilePointerContainer) {
    fileContainers[description] = container

    fileContainerUrlsLock.withLock {
      description.urls.forEach { fileContainerUrls.putValue(it, description) }
      description.jarDirectories.forEach { fileContainerUrls.putValue(it.directoryUrl, description) }
    }
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
