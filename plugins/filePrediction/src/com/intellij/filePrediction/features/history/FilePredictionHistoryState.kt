// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import gnu.trove.TIntIntHashMap
import gnu.trove.TIntObjectHashMap
import org.jdom.Element
import kotlin.math.max

class FilePredictionHistoryState {
  @get:Property(surroundWithTag = true)
  var recentFiles: MutableList<RecentFileEntry> = ArrayList()

  @get:Attribute(value = "prevFile")
  var prevFile: Int? = null

  @get:Attribute(value = "nextCode")
  var nextFileCode: Int = 0

  @Transient
  val root: NGramMapNode = NGramMapNode()

  fun serialize(): Element {
    val serialized = XmlSerializer.serialize(this)
    val sequences = Element("sequences")
    sequences.setAttribute("count", root.count.toString())
    root.usages.forEachEntry { code, node ->
      val child = Element("usage")
      child.setAttribute("code", code.toString())
      child.setAttribute("count", node.count.toString())
      val keys = node.usages.keys().joinToString(separator = ",")
      child.setAttribute("keys", keys)
      val values = node.usages.values.joinToString(separator = ",")
      child.setAttribute("values", values)
      sequences.addContent(child)
      return@forEachEntry true
    }
    serialized.addContent(sequences)
    return serialized
  }

  fun deserialize(element: Element): FilePredictionHistoryState {
    XmlSerializer.deserializeInto(this, element)
    val sequences = element.getChild("sequences")
    if (sequences == null) {
      return this
    }

    root.count = sequences.getIntAttribute("count")
    val usages = sequences.getChildren("usage")
    for (usage in usages) {
      val code = usage.getIntAttribute("code")
      val node = root.getOrCreate(code)
      node.count = usage.getIntAttribute("count")

      val keys = usage.getIntListAttribute("keys")
      val values = usage.getIntListAttribute("values")
      if (keys != null && values != null && keys.size == values.size) {
        for ((index, key) in keys.withIndex()) {
          node.setNode(key, values[index])
        }
      }
    }
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilePredictionHistoryState

    if (recentFiles != other.recentFiles) return false
    if (prevFile != other.prevFile) return false
    if (nextFileCode != other.nextFileCode) return false
    if (root != other.root) return false

    return true
  }

  override fun hashCode(): Int {
    var result = recentFiles.hashCode()
    result = 31 * result + (prevFile ?: 0)
    result = 31 * result + nextFileCode
    result = 31 * result + root.hashCode()
    return result
  }
}

@Tag("recent-file")
class RecentFileEntry {
  @get:Attribute(value = "url")
  var fileUrl: String? = null

  @get:Attribute(value = "code")
  var code: Int = -1

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RecentFileEntry

    if (fileUrl != other.fileUrl) return false
    if (code != other.code) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileUrl?.hashCode() ?: 0
    result = 31 * result + code
    return result
  }
}

abstract class NGramModelNode<T> {
  var count: Int = 0

  abstract fun addOrIncrement(code: Int)

  abstract fun getOrCreate(code: Int): T

  abstract fun getNode(code: Int): T?

  abstract fun findMinMax(): Pair<Int, Int>
}

class NGramMapNode: NGramModelNode<NGramListNode>() {
  val usages: TIntObjectHashMap<NGramListNode> = TIntObjectHashMap()

  override fun addOrIncrement(code: Int) {
    usages.getOrPut(code).count++
  }

  override fun getOrCreate(code: Int): NGramListNode {
    return usages.getOrPut(code)
  }

  override fun getNode(code: Int): NGramListNode? {
    return usages[code]
  }

  fun clear() {
    usages.clear()
  }

  override fun findMinMax(): Pair<Int, Int> {
    var minCount: Int = -1
    var maxCount: Int = -1
    usages.forEachValue { listNode ->
      val count = listNode.count
      if (minCount < 0 || minCount > count) {
        minCount = count
      }
      if (maxCount < 0 || maxCount < count) {
        maxCount = count
      }
      true
    }
    return max(minCount, 0) to max(maxCount, 0)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NGramMapNode

    if (count != other.count) return false
    if (usages != other.usages) return false

    return true
  }

  override fun hashCode(): Int {
    var result = count
    result = 31 * result + usages.hashCode()
    return result
  }
}

class NGramListNode: NGramModelNode<Int>() {
  val usages: TIntIntHashMap = TIntIntHashMap()

  override fun addOrIncrement(code: Int) {
    val current = usages.getOrPut(code)
    usages.put(code, current + 1)
  }

  override fun getOrCreate(code: Int): Int {
    return usages.getOrPut(code)
  }

  override fun getNode(code: Int): Int? {
    return usages.get(code)
  }

  fun setNode(code: Int, count: Int) {
    usages.put(code, count)
  }

  override fun findMinMax(): Pair<Int, Int> {
    var minCount: Int = -1
    var maxCount: Int = -1
    usages.forEachValue { count ->
      if (minCount < 0 || minCount > count) {
        minCount = count
      }
      if (maxCount < 0 || maxCount < count) {
        maxCount = count
      }
      true
    }
    return max(minCount, 0) to max(maxCount, 0)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NGramListNode

    if (count != other.count) return false
    if (usages != other.usages) return false

    return true
  }

  override fun hashCode(): Int {
    var result = count
    result = 31 * result + usages.hashCode()
    return result
  }
}

private fun Element.getIntAttribute(name: String): Int {
  return getAttributeValue(name)?.toIntOrNull() ?: 0
}

private fun Element.getIntListAttribute(name: String): IntArray? {
  return getAttributeValue(name)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray()
}

private fun TIntIntHashMap.getOrPut(code: Int): Int {
  if (!this.containsKey(code)) {
    this.put(code, 0)
  }
  return this.get(code)
}

private fun TIntObjectHashMap<NGramListNode>.getOrPut(code: Int): NGramListNode {
  if (!this.containsKey(code)) {
    this.put(code, NGramListNode())
  }
  return this.get(code)
}
