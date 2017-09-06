/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.loadElement
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.toByteArray
import org.jdom.Element
import java.io.InputStream
import java.util.*

class SchemeManagerIprProvider(private val subStateTagName: String) : StreamProvider {
  private val nameToData = ContainerUtil.newConcurrentMap<String, ByteArray>()

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    nameToData.get(PathUtilRt.getFileName(fileSpec))?.let(ByteArray::inputStream).let { consumer(it) }
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    nameToData.remove(PathUtilRt.getFileName(fileSpec))
    return true
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (String) -> Boolean,
                               processor: (String, InputStream, Boolean) -> Boolean): Boolean {
    for ((name, data) in nameToData) {
      if (filter(name) && !data.inputStream().use { processor(name, it, false) }) {
        break
      }
    }
    return true
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    LOG.assertTrue(content.isNotEmpty())
    nameToData.put(PathUtilRt.getFileName(fileSpec), ArrayUtil.realloc(content, size))
  }

  fun load(state: Element?, nameGetter: ((Element) -> String)? = null) {
    nameToData.clear()

    if (state == null) {
      return
    }

    val nameGenerator = UniqueNameGenerator()
    for (child in state.getChildren(subStateTagName)) {
      var name = nameGetter?.invoke(child) ?: child.getAttributeValue("name")
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
  }

  fun writeState(state: Element, comparator: Comparator<String>? = null) {
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