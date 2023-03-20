// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl.url

import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.storage.impl.IntIdGenerator
import com.intellij.workspaceModel.storage.impl.VirtualFileNameStore
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import it.unimi.dsi.fastutil.Hash.Strategy
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet

open class VirtualFileUrlManagerImpl : VirtualFileUrlManager {
  private val idGenerator = IntIdGenerator()
  private var emptyUrl: VirtualFileUrl? = null
  private val fileNameStore = VirtualFileNameStore()
  private val id2NodeMapping = Int2ObjectOpenHashMap<FilePathNode>()
  private val rootNode = FilePathNode(0, 0)

  @Synchronized
  override fun fromUrl(url: String): VirtualFileUrl {
    if (url.isEmpty()) return getEmptyUrl()
    return add(url)
  }

  override fun findByUrl(url: String): VirtualFileUrl? {
    return findBySegments(splitNames(url)) 
  }

  @Synchronized
  override fun fromUrlSegments(urls: List<String>): VirtualFileUrl {
    if (urls.isEmpty()) return getEmptyUrl()
    return addSegments(null, urls)
  }

  override fun fromPath(path: String): VirtualFileUrl {
    return fromUrl("file://${toSystemIndependentName(path)}")
  }

  private fun toSystemIndependentName(fileName: String): String {
    return fileName.replace('\\', '/')
  }

  @Synchronized
  override fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl? {
    vfu as VirtualFileUrlImpl
    return id2NodeMapping.get(vfu.id)?.parent?.getVirtualFileUrl(this)
  }

  @Synchronized
  override fun getSubtreeVirtualUrlsById(vfu: VirtualFileUrl): List<VirtualFileUrl>  {
    vfu as VirtualFileUrlImpl
    return id2NodeMapping.get(vfu.id).getSubtreeNodes().map { it.getVirtualFileUrl(this) }
  }

  override fun processChildrenRecursively(url: VirtualFileUrl, processor: (VirtualFileUrl) -> TreeNodeProcessingResult): Boolean {
    val node = synchronized(this) { id2NodeMapping.get ((url as VirtualFileUrlImpl).id) }
    return node.processChildrenRecursively {
      val childUrl = synchronized(this) { it.getVirtualFileUrl (this) }
      processor(childUrl)
    }
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
    return add(relativePath, id2NodeMapping.get(parentVfu.id))
  }

  protected open fun createVirtualFileUrl(id: Int, manager: VirtualFileUrlManagerImpl): VirtualFileUrl {
    return VirtualFileUrlImpl(id, manager)
  }

  fun getCachedVirtualFileUrls(): List<VirtualFileUrl> = id2NodeMapping.values.mapNotNull(FilePathNode::getCachedVirtualFileUrl)

  internal fun add(path: String, parentNode: FilePathNode? = null): VirtualFileUrl {
    val segments = splitNames(path)
    return addSegments(parentNode, segments)
  }

  private fun addSegments(parentNode: FilePathNode?, segments: List<String>): VirtualFileUrl {
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
          return newNode.getVirtualFileUrl(this)
        }
        latestNode = newNode
        rootNode.addChild(newNode)
        continue
      }

      if (latestNode === findRootNode(latestNode.contentId)) {
        if (latestNode.contentId == nameId) {
          if (index == latestElement) return latestNode.getVirtualFileUrl(this)
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
        if (index == latestElement) return newNode.getVirtualFileUrl(this)
      }
      else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) return node.getVirtualFileUrl(this)
        latestNode = node
      }
    }
    return getEmptyUrl()
  }
  
  private fun findBySegments(segments: List<String>): VirtualFileUrl? {
    var currentNode = rootNode
    for (segment in segments) {
      val nameId = fileNameStore.getIdForName(segment) ?: return null
      currentNode = currentNode.findChild(nameId) ?: return null
    }
    return currentNode.getCachedVirtualFileUrl()
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
    add(newPath)
  }

  private fun getEmptyUrl(): VirtualFileUrl {
    if (emptyUrl == null) {
      emptyUrl = createVirtualFileUrl(0, this)
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
    private var children: ObjectOpenCustomHashSet<FilePathNode>? = null

    fun findChild(nameId: Int): FilePathNode? {
      return children?.get(FilePathNode(0, nameId))
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
    
    fun processChildrenRecursively(processor: (FilePathNode) -> TreeNodeProcessingResult): Boolean {
      val childrenCopy = synchronized(this@VirtualFileUrlManagerImpl) { children?.clone() }
      childrenCopy?.forEach { child ->
        when (processor(child)) {
          TreeNodeProcessingResult.CONTINUE -> {
            if (!child.processChildrenRecursively(processor)) {
              return false
            }
          } 
          TreeNodeProcessingResult.SKIP_CHILDREN -> {}
          TreeNodeProcessingResult.SKIP_TO_PARENT -> return true
          TreeNodeProcessingResult.STOP -> return false
        }
      }
      return true
    }

    fun addChild(newNode: FilePathNode) {
      createChildrenList()
      children!!.add(newNode)
    }

    fun removeChild(node: FilePathNode) {
      children?.remove(node)
    }

    fun getVirtualFileUrl(virtualFileUrlManager: VirtualFileUrlManagerImpl): VirtualFileUrl {
      val cachedValue = virtualFileUrl
      if (cachedValue != null) return cachedValue
      val url = virtualFileUrlManager.createVirtualFileUrl(nodeId, virtualFileUrlManager)
      virtualFileUrl = url
      return url
    }

    fun getCachedVirtualFileUrl(): VirtualFileUrl? = virtualFileUrl

    fun isEmpty() = children == null || children!!.isEmpty()

    private fun createChildrenList() {
      if (children == null) children = ObjectOpenCustomHashSet(HASHING_STRATEGY)
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

  private companion object {
    val HASHING_STRATEGY: Strategy<FilePathNode> = object : Strategy<FilePathNode> {
      override fun equals(node1: FilePathNode?, node2: FilePathNode?): Boolean {
        if (node1 === node2) {
          return true
        }
        if (node1 == null || node2 == null) {
          return false
        }
        return node1.contentId == node2.contentId
      }

      override fun hashCode(node: FilePathNode?): Int = node?.contentId ?: 0
    }
  }
}