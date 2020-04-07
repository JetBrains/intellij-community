// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil
import java.util.*

fun main() {
  val store = Store()
  store.add("/private/var/folders/dw/T/unitTest_importModule/idea_test_/oyt.dmg", 20)
  store.add("/private/var/folders/dw/T/oyt.dmg", 15)
  store.add("/private/var/folders/dt/idea_test_/oyt.dmg", 7)
  store.add("/private/var/folders/dk/T/unitTest_importModule/idea_test_/oyt.dmg", 6)
  store.add("/private/var/folders/do/T/unitTest_importModule/oyt.dmg", 8)
  println("")
  println(store.findLatestFilePathNode("/private/var/folders/dk/T/unitTest_importModule/idea_test_/oyt.dmg")?.values)
  val values = LinkedHashSet<Int>(5)
  println(values.isEmpty())
}

class FilePathNode(val content: String, val parent: FilePathNode? = null) {
  val values: MutableSet<Int> = mutableSetOf()
  val children: MutableSet<FilePathNode> = mutableSetOf()
}

class Store {
  private var rootNode: FilePathNode? = null

  //fun add(path: String, entity: PEntityData<out TypedEntity>) {
  fun add(path: String, entityId: Int) {
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val name = names[index]
      // Latest node can be NULL only if it's root node
      if (latestNode == null) {
        val newNode = FilePathNode(name)
        // If it's the latest name of folder or files, save entity Id as node value
        if (index == 0) {
          newNode.values.add(entityId)
          return
        }
        latestNode = newNode
        rootNode = newNode
        continue
      }

      if (latestNode === rootNode) {
        if (latestNode.content == name) {
          if (index == 0) {
            latestNode.values.add(entityId)
            return
          }
          continue
        }
      }

      val node = latestNode.children.find { it.content == name }
      if (node == null) {
        val newNode = FilePathNode(name, latestNode)
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
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val name = names[index]
      // Latest node can be NULL only if it's root node
      if (latestNode == null) {
        println("Path doesn't exist")
        return
      }

      if (latestNode === rootNode) {
        if (latestNode.content != name) {
          println("Path doesn't exist")
          return
        } else {
          if (index == 0) {
            if (!latestNode.values.remove(entityId)) {
              println("EntityId $entityId doesn't exist")
              return
            }
            if (latestNode.values.isEmpty() && latestNode.children.isEmpty()) {
              rootNode = null
              return
            }
          } else continue
        }
      }

      val node = latestNode.children.find { it.content == name }
      if (node == null) {
        println("Path doesn't exist")
      } else {
        if (index == 0) {
          if (!node.values.remove(entityId)) {
            println("EntityId $entityId doesn't exist")
            return
          }
          if (latestNode.values.isEmpty() && latestNode.children.isEmpty()) {
            rootNode = null
            return
          }
          node.values.add(entityId)
          return
        }
        latestNode = node
      }
    }
  }

  //fun update(oldPath: String, newPath: String, entity: PEntityData<out TypedEntity>) {
  fun update(oldPath: String, newPath: String, entityId: Int) {
    remove(oldPath, entityId)
    add(newPath, entityId)
  }

  fun findLatestFilePathNode(path: String): FilePathNode? {
    val names = splitNames(path)
    var latestNode: FilePathNode? = rootNode
    for (index in names.indices.reversed()) {
      val name = names[index]
      // Latest node can be NULL only if it's root node
      if (latestNode == null) return null

      if (latestNode === rootNode) {
        if (latestNode.content == name) {
          if (index == 0) return latestNode else continue
        }
      }

      latestNode.children.find { it.content == name }?.let {
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
}