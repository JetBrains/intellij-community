// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
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
    for (entry in root.usages.int2ObjectEntrySet()) {
      val child = Element("usage")
      child.setAttribute("code", entry.intKey.toString())
      child.setAttribute("count", entry.value.count.toString())
      val keys = entry.value.usages.keys.joinToString(separator = ",")
      child.setAttribute("keys", keys)
      val values = entry.value.usages.values.joinToString(separator = ",")
      child.setAttribute("values", values)
      sequences.addContent(child)
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
  val usages = Int2ObjectOpenHashMap<NGramListNode>()

  override fun addOrIncrement(code: Int) {
    getOrPut(usages, code).count++
  }

  override fun getOrCreate(code: Int): NGramListNode {
    return getOrPut(usages, code)
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
    for (listNode in usages.values) {
      val count = listNode.count
      if (minCount < 0 || minCount > count) {
        minCount = count
      }
      if (maxCount < 0 || maxCount < count) {
        maxCount = count
      }
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
  val usages: Int2IntOpenHashMap = Int2IntOpenHashMap()

  override fun addOrIncrement(code: Int) {
    usages.addTo(code, 1)
  }

  override fun getOrCreate(code: Int): Int {
    return usages.addTo(code, 0)
  }

  override fun getNode(code: Int): Int {
    return usages.get(code)
  }

  fun setNode(code: Int, count: Int) {
    usages.put(code, count)
  }

  override fun findMinMax(): Pair<Int, Int> {
    var minCount: Int = -1
    var maxCount: Int = -1
    val iterator = usages.values.iterator()
    while (iterator.hasNext()) {
      val count = iterator.nextInt()
      if (minCount < 0 || minCount > count) {
        minCount = count
      }
      if (maxCount < 0 || maxCount < count) {
        maxCount = count
      }
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

private fun getOrPut(int2ObjectOpenHashMap: Int2ObjectOpenHashMap<NGramListNode>, code: Int): NGramListNode {
  if (!int2ObjectOpenHashMap.containsKey(code)) {
    int2ObjectOpenHashMap.put(code, NGramListNode())
  }
  return int2ObjectOpenHashMap.get(code)
}
