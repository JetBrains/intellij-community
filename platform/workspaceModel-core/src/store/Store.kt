// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil
import com.intellij.workspace.api.VirtualFileUrl
import java.util.*
import kotlin.collections.HashMap

class Store {
  private var rootNode: FilePathNode? = null
  private val idGenerator= IdGenerator()
  private val id2NodeMapping = HashMap<Int, FilePathNode>()
  private val fileNameStore = FileNameStore()

  companion object {
    private val EMPTY_URL = VirtualFileUrl(0)
  }

  //url.split('/', '\\')
  fun fromUrl(url: String): VirtualFileUrl = add(url, 1)

  fun fromPath(path: String): VirtualFileUrl = fromUrl("file://${FileUtil.toSystemIndependentName(path)}")

  internal fun getParentVirtualUrlById(id: Int): VirtualFileUrl? = id2NodeMapping[id]?.parent?.let { VirtualFileUrl(it.nodeId) }

  internal fun getUrlById(id: Int): String {
    if (id <= 0) return ""

    var node = id2NodeMapping[id]
    val builder = StringBuilder()
    while (node != null) {
      builder.append(fileNameStore.getNameForId(node.contentId))
      node = node.parent
    }
    return builder.reverse().toString()
  }

  //fun add(path: String, entity: PEntityData<out TypedEntity>) {
  fun add(path: String, entityId: Int): VirtualFileUrl  {
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val nameId = fileNameStore.generateIdForName(names[index])
      // Latest node can be NULL only if it's root node
      if (latestNode == null) {
        val nodeId = idGenerator.generateId()
        val newNode = FilePathNode(nodeId, nameId)
        id2NodeMapping[nodeId] = newNode
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          newNode.values.add(entityId)
          rootNode = newNode
          return VirtualFileUrl(nodeId)
        }
        latestNode = newNode
        rootNode = newNode
        continue
      }

      if (latestNode === rootNode) {
        if (latestNode.contentId == nameId) {
          if (index == 0) {
            latestNode.values.add(entityId)
            return VirtualFileUrl(latestNode.nodeId)
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
        if (index == 0) {
          newNode.values.add(entityId)
          return VirtualFileUrl(nodeId)
        }
      } else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          node.values.add(entityId)
          return VirtualFileUrl(node.nodeId)

        }
        latestNode = node
      }
    }
    return EMPTY_URL
  }

  //fun remove(path: String, entity: PEntityData<out TypedEntity>) {
  fun remove(path: String, entityId: Int) {
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
        if (currentNode === rootNode && currentNode.values.isEmpty() && currentNode.children.isEmpty()) {
          removeNameUsage(currentNode.contentId)
          idGenerator.releaseId(currentNode.nodeId)
          id2NodeMapping.remove(currentNode.nodeId)
          rootNode = null
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

  //fun update(oldPath: String, newPath: String, entity: PEntityData<out TypedEntity>) {
  fun update(oldPath: String, newPath: String, entityId: Int) {
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
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val nameId = fileNameStore.getIdForName(names[index]) ?: return null
      // Latest node can be NULL only if it's root node
      if (latestNode == null) return null

      if (latestNode === rootNode) {
        if (latestNode.contentId == nameId) {
          if (index == 0) return latestNode else continue
        }
      }

      latestNode.children.find { it.contentId == nameId }?.let {
        if (index == 0) return it
        latestNode = it
      } ?: return null
    }
    return null
  }

  private fun splitNames(path: String): List<String> {
    val names: MutableList<String> = ArrayList(20)
    var end = path.length
    if (end == 0) return names
    while (true) {
      val startIndex: Int = extractName(path, end)
      assert(startIndex != end) {
        "startIndex: " + startIndex + "; end: " + end + "; path:'" + path + "'; toExtract: '" + path.substring(0, end) + "'"
      }

      names.add(path.substring(startIndex, end))
      if (startIndex == 0) {
        break
      }
      val skipSeparator = if (StringUtil.endsWith(path, 0, startIndex, URLUtil.JAR_SEPARATOR) && startIndex > 2 && path[startIndex - 3] != '/') 2
      else 1
      end = startIndex - skipSeparator
      if (end == 0 && path[0] == '/') {
        end = 1 // here's this weird ROOT file in temp system
      }
    }
    return names
  }

  // returns start index of the name (i.e. path[return..length) is considered a name)
  private fun extractName(path: CharSequence, length: Int): Int {
    if (length == 1 && path[0] == '/') {
      return 0 // in case of TEMP file system there is this weird ROOT file
    }
    val i = StringUtil.lastIndexOf(path, '/', 0, length)
    return i + 1
  }

  override fun toString() = "$rootNode"

  private inner class FilePathNode(val nodeId: Int, val contentId: Int, val parent: FilePathNode? = null) {
    val values: MutableSet<Int> = mutableSetOf()
    val children: MutableSet<FilePathNode> = mutableSetOf()

    override fun toString(): String {
      val buffer = StringBuilder()
      print(buffer, "", "")
      return buffer.toString()
    }

    private fun print(buffer: StringBuilder, prefix: String, childrenPrefix: String) {
      val name = this@Store.fileNameStore.getNameForId(contentId)
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
}