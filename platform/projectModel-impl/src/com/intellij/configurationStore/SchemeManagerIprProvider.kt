// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.*
import com.intellij.util.text.UniqueNameGenerator
import org.jdom.Element
import java.io.InputStream
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.LinkedHashMap
import kotlin.concurrent.read
import kotlin.concurrent.write

class SchemeManagerIprProvider(private val subStateTagName: String, private val comparator: Comparator<String>? = null) : StreamProvider {
  private val lock = ReentrantReadWriteLock()
  private var nameToData = LinkedHashMap<String, ByteArray>()

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    lock.read {
      nameToData.get(PathUtilRt.getFileName(fileSpec))?.let(ByteArray::inputStream).let { consumer(it) }
    }
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    lock.write {
      nameToData.remove(PathUtilRt.getFileName(fileSpec))
    }
    return true
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (String) -> Boolean,
                               processor: (String, InputStream, Boolean) -> Boolean): Boolean {
    lock.read {
      for ((name, data) in nameToData) {
        if (filter(name) && !data.inputStream().use { processor(name, it, false) }) {
          break
        }
      }
    }
    return true
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    LOG.assertTrue(content.isNotEmpty())
    lock.write {
      nameToData.put(PathUtilRt.getFileName(fileSpec), ArrayUtil.realloc(content, size))
    }
  }

  fun load(state: Element?, keyGetter: ((Element) -> String)? = null) {
    if (state == null) {
      lock.write {
        nameToData.clear()
      }
      return
    }

    val nameToData = LinkedHashMap<String, ByteArray>()
    val nameGenerator = UniqueNameGenerator()
    for (child in state.getChildren(subStateTagName)) {
      // https://youtrack.jetbrains.com/issue/RIDER-10052
      // ignore empty elements
      if (child.isEmpty()) {
        continue
      }

      var name = keyGetter?.invoke(child) ?: child.getAttributeValue("name")
      if (name == null) {
        for (optionElement in child.getChildren("option")) {
          if (optionElement.getAttributeValue("name") == "myName") {
            name = optionElement.getAttributeValue("value")
          }
        }
      }

      if (name.isNullOrEmpty()) {
        continue
      }

      nameToData.put(nameGenerator.generateUniqueName("${FileUtil.sanitizeFileName(name, false)}.xml"), child.toByteArray())
    }

    lock.write {
      if (comparator == null) {
        this.nameToData = nameToData
      }
      else {
        this.nameToData.clear()
        this.nameToData.putAll(nameToData.toSortedMap(comparator))
      }
    }
  }

  fun writeState(state: Element) {
    lock.read {
      val names = nameToData.keys.toTypedArray()
      if (comparator == null) {
        names.sort()
      }
      else {
        names.sortWith(comparator)
      }
      for (name in names) {
        nameToData.get(name)?.let { state.addContent(loadElement(it.inputStream())) }
      }
    }
  }
}