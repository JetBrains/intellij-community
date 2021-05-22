// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.url

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.storage.impl.IntIdGenerator
import com.intellij.workspaceModel.storage.impl.VirtualFileNameStore
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

open class VirtualFileUrlManagerImpl : VirtualFileUrlManager {
  private val idGenerator = IntIdGenerator()
  private var emptyUrl: VirtualFileUrl? = null
  private val fileNameStore = VirtualFileNameStore()
  private val id2NodeMapping = Int2ObjectOpenHashMap<FilePathNode>()
  private val rootNode = FilePathNode(0, 0)

  @Synchronized
  override fun fromUrl(url: String): VirtualFileUrl {
    if (url.isEmpty()) return getEmptyUrl()
    return add(url, getProtocol(url))
  }

  override fun fromPath(path: String): VirtualFileUrl {
    return fromUrl("file://${FileUtil.toSystemIndependentName(path)}")
  }

  @Synchronized
  override fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl? {
    vfu as VirtualFileUrlImpl
    return id2NodeMapping.get(vfu.id)?.parent?.getVirtualFileUrl(this, getProtocol(vfu.url))
  }

  @Synchronized
  override fun getSubtreeVirtualUrlsById(vfu: VirtualFileUrl): List<VirtualFileUrl>  {
    vfu as VirtualFileUrlImpl
    val protocol = getProtocol(vfu.url)
    return id2NodeMapping.get(vfu.id).getSubtreeNodes().map { it.getVirtualFileUrl(this, protocol) }
  }

  @Synchronized
  fun getUrlById(id: Int): String {
    if (id <= 0) return ""

    var node = id2NodeMapping[id]
    val contentIds = IntArrayList()
    while (node != null) {
      contentIds.add(node.contentId)
      node = node.parent
    }

    if (contentIds.size == 1) {
      return fileNameStore.getNameForId(contentIds.getInt(0))?.let {
        return@let if (it.isEmpty()) "/" else it
      } ?: ""
    }
    val builder = StringBuilder()
    for (index in contentIds.size - 1 downTo 0) {
      builder.append(fileNameStore.getNameForId(contentIds.getInt(index)))
      if (index != 0) builder.append("/")
    }
    return builder.toString()
  }

  @Synchronized
  internal fun append(parentVfu: VirtualFileUrl, relativePath: String): VirtualFileUrl {
    parentVfu as VirtualFileUrlImpl
    return add(relativePath, getProtocol(parentVfu.url), id2NodeMapping.get(parentVfu.id))
  }

  protected open fun createVirtualFileUrl(id: Int, manager: VirtualFileUrlManagerImpl, protocol: String?): VirtualFileUrl {
    return VirtualFileUrlImpl(id, manager)
  }

  internal fun add(path: String, protocol: String? = null, parentNode: FilePathNode? = null): VirtualFileUrl {
    val segments = splitNames(path)
    var latestNode: FilePathNode? = parentNode ?: findRootNode(segments.first())
    val latestElement = segments.size - 1
    for (index in segments.indices) {
      val nameId = fileNameStore.generateIdForName(segments[index])
      // Latest node can be NULL only if it's root node
      if (latestNode == null) {
        val nodeId = idGenerator.generateId()
        val newNode = FilePathNode(nodeId, nameId)
        id2NodeMapping[nodeId] = newNode
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) {
          rootNode.addChild(newNode)
          return newNode.getVirtualFileUrl(this, protocol)
        }
        latestNode = newNode
        rootNode.addChild(newNode)
        continue
      }

      if (latestNode === findRootNode(latestNode.contentId)) {
        if (latestNode.contentId == nameId) {
          if (index == latestElement) return latestNode.getVirtualFileUrl(this, protocol)
          continue
        }
      }

      val node = latestNode.findChild(nameId)
      if (node == null) {
        val nodeId = idGenerator.generateId()
        val newNode = FilePathNode(nodeId, nameId, latestNode)
        id2NodeMapping[nodeId] = newNode
        latestNode.addChild(newNode)
        latestNode = newNode
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) return newNode.getVirtualFileUrl(this, protocol)
      }
      else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) return node.getVirtualFileUrl(this, protocol)
        latestNode = node
      }
    }
    return getEmptyUrl()
  }

  internal fun remove(path: String) {
    val node = findLatestFilePathNode(path)
    if (node == null) {
      println("File not found")
      return
    }
    if (!node.isEmpty()) return

    var currentNode: FilePathNode = node
    do {
      val parent = currentNode.parent
      if (parent == null) {
        if (currentNode === findRootNode(currentNode.contentId) && currentNode.isEmpty()) {
          removeNameUsage(currentNode.contentId)
          id2NodeMapping.remove(currentNode.nodeId)
          rootNode.removeChild(currentNode)
        }
        return
      }

      parent.removeChild(currentNode)
      removeNameUsage(currentNode.contentId)
      id2NodeMapping.remove(currentNode.nodeId)
      currentNode = parent
    }
    while (currentNode.isEmpty())
  }

  internal fun update(oldPath: String, newPath: String) {
    val latestPathNode = findLatestFilePathNode(oldPath)
    if (latestPathNode == null) return
    remove(oldPath)
    add(newPath, getProtocol(newPath))
  }

  private fun getEmptyUrl(): VirtualFileUrl {
    if (emptyUrl == null) {
      emptyUrl = createVirtualFileUrl(0, this, null)
    }
    return emptyUrl!!
  }

  private fun removeNameUsage(contentId: Int) {
    val name = fileNameStore.getNameForId(contentId)
    assert(name != null)
    fileNameStore.removeName(name!!)
  }

  private fun findLatestFilePathNode(path: String): FilePathNode? {
    val segments = splitNames(path)
    var latestNode: FilePathNode? = findRootNode(segments.first())
    val latestElement = segments.size - 1
    for (index in segments.indices) {
      val nameId = fileNameStore.getIdForName(segments[index]) ?: return null
      // Latest node can be NULL only if it's root node
      if (latestNode == null) return null

      if (latestNode === findRootNode(latestNode.contentId)) {
        if (latestNode.contentId == nameId) {
          if (index == latestElement) return latestNode else continue
        }
      }

      latestNode.findChild(nameId)?.let {
        if (index == latestElement) return it
        latestNode = it
      } ?: return null
    }
    return null
  }

  private fun findRootNode(segment: String): FilePathNode? {
    val segmentId = fileNameStore.getIdForName(segment) ?: return null
    return rootNode.findChild(segmentId)
  }

  private fun findRootNode(contentId: Int): FilePathNode? = rootNode.findChild(contentId)

  private fun splitNames(path: String): List<String> = path.split('/', '\\')

  private fun getProtocol(url: String): String? {
    val protocolEnd = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    return if (protocolEnd != -1) url.substring(0, protocolEnd) else null
  }

  fun print() = rootNode.print()

  fun isEqualOrParentOf(parentNodeId: Int, childNodeId: Int): Boolean {
    if (parentNodeId == 0 && childNodeId == 0) return true

    var current = childNodeId
    while (current > 0) {
      if (parentNodeId == current) return true
      current = id2NodeMapping[current]?.parent?.nodeId ?: return false
    }
    /*
    TODO It may look like this + caching + invalidating
    val segmentName = getSegmentName(id).toString()

    val parent = id2parent.getValue(id)
    val parentParent = id2parent.getValue(parent)
    return if (parentParent <= 0) {
      val fileSystem = VirtualFileManager.getInstance().getFileSystem(getSegmentName(parent).toString())
      fileSystem?.findFileByPath(segmentName)
    } else {
      getVirtualFileById(parent)?.findChild(segmentName)
    }
    }
    */
    return false
  }

  internal inner class FilePathNode(val nodeId: Int, val contentId: Int, val parent: FilePathNode? = null) {
    private var virtualFileUrl: VirtualFileUrl? = null
    private var children: MutableList<FilePathNode>? = null

    fun findChild(nameId: Int): FilePathNode? {
      // If search of child node will be slow, replace SmartList to THashSet
      // For now SmartList reduce 500Kb memory on IDEA project
      return children?.find { it.contentId == nameId }
    }

    fun getSubtreeNodes(): List<FilePathNode> {
      return getSubtreeNodes(mutableListOf())
    }

    private fun getSubtreeNodes(subtreeNodes: MutableList<FilePathNode>): List<FilePathNode> {
      children?.forEach {
        subtreeNodes.add(it)
        it.getSubtreeNodes(subtreeNodes)
      }
      return subtreeNodes
    }

    fun addChild(newNode: FilePathNode) {
      createChildrenList()
      children!!.add(newNode)
    }

    fun removeChild(node: FilePathNode) {
      children?.remove(node)
    }

    fun getVirtualFileUrl(virtualFileUrlManager: VirtualFileUrlManagerImpl, protocol: String?): VirtualFileUrl {
      val cachedValue = virtualFileUrl
      if (cachedValue != null) return cachedValue
      val url = virtualFileUrlManager.createVirtualFileUrl(nodeId, virtualFileUrlManager, protocol)
      virtualFileUrl = url
      return url
    }

    fun isEmpty() = children == null || children!!.isEmpty()

    private fun createChildrenList() {
      if (children == null) children = SmartList()
    }

    fun print(): String {
      val buffer = StringBuilder()
      print(buffer, "", "")
      return buffer.toString()
    }

    private fun print(buffer: StringBuilder, prefix: String, childrenPrefix: String) {
      val name = this@VirtualFileUrlManagerImpl.fileNameStore.getNameForId(contentId)
      if (name != null) buffer.append("$prefix $name\n")
      val iterator = children?.iterator() ?: return
      while (iterator.hasNext()) {
        val next = iterator.next()
        if (name == null) {
          next.print(buffer, childrenPrefix, childrenPrefix)
          continue
        }
        if (iterator.hasNext()) {
          next.print(buffer, "$childrenPrefix |- ", "$childrenPrefix |   ")
        }
        else {
          next.print(buffer, "$childrenPrefix '- ", "$childrenPrefix     ")
        }
      }
    }
  }
}