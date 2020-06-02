// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.VirtualFileUrl
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

class VirtualFileUrlManagerImpl : VirtualFileUrlManager {
  private val idGenerator = IntIdGenerator()
  private val EMPTY_URL = VirtualFileUrl(0, this)
  private val fileNameStore = VirtualFileNameStore()
  private val id2NodeMapping = Int2ObjectOpenHashMap<FilePathNode>()
  private val rootNode = FilePathNode(0, 0)

  @Synchronized
  override fun fromUrl(url: String): VirtualFileUrl {
    if (url.isEmpty()) return EMPTY_URL
    return add(url)
  }

  override fun fromPath(path: String): VirtualFileUrl {
    return fromUrl("file://${FileUtil.toSystemIndependentName(path)}")
  }

  @Synchronized
  override fun getParentVirtualUrlById(id: Int): VirtualFileUrl? = id2NodeMapping.get(id)?.parent?.let {
    VirtualFileUrl(it.nodeId, this)
  }

  @Synchronized
  override fun getUrlById(id: Int): String {
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

  internal fun add(path: String): VirtualFileUrl {
    val segments = splitNames(path)
    var latestNode: FilePathNode? = findRootNode(segments.first())
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
          return VirtualFileUrl(nodeId, this)
        }
        latestNode = newNode
        rootNode.addChild(newNode)
        continue
      }

      if (latestNode === findRootNode(latestNode.contentId)) {
        if (latestNode.contentId == nameId) {
          if (index == latestElement) return VirtualFileUrl(
            latestNode.nodeId, this)
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
        if (index == latestElement) return VirtualFileUrl(nodeId, this)
      }
      else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) return VirtualFileUrl(node.nodeId, this)
        latestNode = node
      }
    }
    return EMPTY_URL
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

  override fun isEqualOrParentOf(parentNodeId: Int, childNodeId: Int): Boolean {
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

  private inner class FilePathNode(val nodeId: Int, val contentId: Int, val parent: FilePathNode? = null) {
    private var children: MutableList<FilePathNode>? = null

    fun findChild(nameId: Int): FilePathNode? {
      // If search of child node will be slow, replace SmartList to THashSet
      // For now SmartList reduce 500Kb memory on IDEA project
      return children?.find { it.contentId == nameId }
    }

    fun addChild(newNode: FilePathNode) {
      createChildrenList()
      children!!.add(newNode)
    }

    fun removeChild(node: FilePathNode) {
      children?.remove(node)
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