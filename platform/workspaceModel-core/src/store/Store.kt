// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil
import java.util.*

class Store {
  private var rootNode: FilePathNode? = null
  private val fileNameStore = FileNameStore()

  //fun add(path: String, entity: PEntityData<out TypedEntity>) {
  fun add(path: String, entityId: Int) {
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val nameId = fileNameStore.generateIdForName(names[index])
      // Latest node can be NULL only if it's root node
      if (latestNode == null) {
        val newNode = FilePathNode(nameId)
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          newNode.values.add(entityId)
          rootNode = newNode
          return
        }
        latestNode = newNode
        rootNode = newNode
        continue
      }

      if (latestNode === rootNode) {
        if (latestNode.contentId == nameId) {
          if (index == 0) {
            latestNode.values.add(entityId)
            return
          }
          continue
        }
      }

      val node = latestNode.children.find { it.contentId == nameId }
      if (node == null) {
        val newNode = FilePathNode(nameId, latestNode)
        latestNode.children.add(newNode)
        latestNode = newNode
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          newNode.values.add(entityId)
          return
        }
      } else {
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          node.values.add(entityId)
          return
        }
        latestNode = node
      }
    }
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
          rootNode = null
        }
        return
      }

      parent.children.remove(currentNode)
      removeNameUsage(currentNode.contentId)
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

  private fun removeNameUsage(contentId: Long) {
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

  private inner class FilePathNode(val contentId: Long, val parent: FilePathNode? = null) {
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