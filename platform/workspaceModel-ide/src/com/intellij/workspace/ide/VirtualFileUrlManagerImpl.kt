// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.VirtualFileUrlManager
import kotlin.collections.set

class VirtualFileUrlManagerImpl: VirtualFileUrlManager() {
  private val idGenerator= IdGenerator()
  private val EMPTY_URL = VirtualFileUrl(0, this)
  private val fileNameStore = VirtualFileNameStore()
  private val id2NodeMapping = HashMap<Int, FilePathNode>()
  private var segmentId2RootNodeMapping = mutableMapOf<Int, FilePathNode>()

  companion object {
    fun getInstance(project: Project): VirtualFileUrlManager = project.service<VirtualFileUrlManagerImpl>()
  }

  override fun fromUrl(url: String): VirtualFileUrl {
    if (url.isEmpty()) return EMPTY_URL
    return add(url, 1)
  }

  override fun fromPath(path: String): VirtualFileUrl {
    if (path.isEmpty()) return EMPTY_URL
    return fromUrl("file://${FileUtil.toSystemIndependentName(path)}")
  }

  override fun getParentVirtualUrlById(id: Int): VirtualFileUrl? = id2NodeMapping[id]?.parent?.let { VirtualFileUrl(it.nodeId, this) }

  override fun getUrlById(id: Int): String {
    if (id <= 0) return ""

    var node = id2NodeMapping[id]
    val contentIds = mutableListOf<Int>()
    while (node != null) {
      contentIds.add(node.contentId)
      node = node.parent
    }

    if (contentIds.size == 1) {
      return fileNameStore.getNameForId(contentIds[0])?.let {
        return@let if (it.isEmpty()) "/" else it
      } ?: ""
    }
    val builder = StringBuilder()
    for (index in contentIds.indices.reversed()) {
      builder.append(fileNameStore.getNameForId(contentIds[index]))
      if (index != 0) builder.append("/")
    }
    return builder.toString()
  }

  internal fun add(path: String, entityId: Int): VirtualFileUrl {
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
          newNode.values.add(entityId)
          segmentId2RootNodeMapping[nameId] = newNode
          return VirtualFileUrl(nodeId, this)
        }
        latestNode = newNode
        segmentId2RootNodeMapping[nameId] = newNode
        continue
      }

      if (latestNode === findRootNode(latestNode.contentId)) {
        if (latestNode.contentId == nameId) {
          if (index == latestElement) {
            latestNode.values.add(entityId)
            return VirtualFileUrl(latestNode.nodeId, this)
          }
          continue
        }
      }

      val node = latestNode.children.find { it.contentId == nameId }
      if (node == null) {
        val nodeId = idGenerator.generateId()
        val newNode = FilePathNode(nodeId, nameId, latestNode)
        id2NodeMapping[nodeId] = newNode
        latestNode.children.add(newNode)
        latestNode = newNode
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) {
          newNode.values.add(entityId)
          return VirtualFileUrl(nodeId, this)
        }
      } else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == latestElement) {
          node.values.add(entityId)
          return VirtualFileUrl(node.nodeId, this)

        }
        latestNode = node
      }
    }
    return EMPTY_URL
  }

  internal fun remove(path: String, entityId: Int) {
    val node = findLatestFilePathNode(path)
    if (node == null || !node.values.remove(entityId)) {
      println("File not found")
      return
    }
    if (node.values.isNotEmpty() || node.children.isNotEmpty()) return

    var currentNode: FilePathNode = node
    do {
      val parent = currentNode.parent
      if (parent == null) {
        if (currentNode === findRootNode(currentNode.contentId) && currentNode.values.isEmpty() && currentNode.children.isEmpty()) {
          removeNameUsage(currentNode.contentId)
          idGenerator.releaseId(currentNode.nodeId)
          id2NodeMapping.remove(currentNode.nodeId)
          segmentId2RootNodeMapping.remove(currentNode.contentId)
        }
        return
      }

      parent.children.remove(currentNode)
      removeNameUsage(currentNode.contentId)
      idGenerator.releaseId(currentNode.nodeId)
      id2NodeMapping.remove(currentNode.nodeId)
      currentNode = parent
    } while (currentNode.values.isEmpty() && currentNode.children.isEmpty())
  }

  internal fun update(oldPath: String, newPath: String, entityId: Int) {
    val latestPathNode = findLatestFilePathNode(oldPath)
    if (latestPathNode == null || !latestPathNode.values.contains(entityId)) return
    remove(oldPath, entityId)
    add(newPath, entityId)
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

      latestNode.children.find { it.contentId == nameId }?.let {
        if (index == latestElement) return it
        latestNode = it
      } ?: return null
    }
    return null
  }

  private fun findRootNode(segment: String): FilePathNode? {
    val segmentId = fileNameStore.getIdForName(segment) ?: return null
    return segmentId2RootNodeMapping[segmentId]
  }

  private fun findRootNode(contentId: Int): FilePathNode? = segmentId2RootNodeMapping[contentId]

  private fun splitNames(path: String): List<String> = path.split('/', '\\')

  override fun toString() = segmentId2RootNodeMapping.values.joinToString(separator = "\n") { it.toString() }

  private inner class FilePathNode(val nodeId: Int, val contentId: Int, val parent: FilePathNode? = null) {
    val values: MutableSet<Int> = mutableSetOf()
    val children: MutableSet<FilePathNode> = mutableSetOf()

    override fun toString(): String {
      val buffer = StringBuilder()
      print(buffer, "", "")
      return buffer.toString()
    }

    private fun print(buffer: StringBuilder, prefix: String, childrenPrefix: String) {
      val name = this@VirtualFileUrlManagerImpl.fileNameStore.getNameForId(contentId)
      if (values.isEmpty()) buffer.append("$prefix $name\n") else buffer.append("$prefix $name => $values\n")
      val iterator = children.iterator()
      while (iterator.hasNext()) {
        val next = iterator.next()
        if (iterator.hasNext()) {
          next.print(buffer, "$childrenPrefix |- ", "$childrenPrefix |   ")
        }
        else {
          next.print(buffer, "$childrenPrefix '- ", "$childrenPrefix     ")
        }
      }
    }
  }

  override fun isEqualOrParentOf(parentNodeId: Int, childNodeId: Int): Boolean {
    if (parentNodeId == 0 && childNodeId == 0) return true

    var current = childNodeId
    while (current > 0) {
      if (parentNodeId == current) return true
      current = id2NodeMapping[current]?.parent?.nodeId ?: return false
    }
    return false
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
}