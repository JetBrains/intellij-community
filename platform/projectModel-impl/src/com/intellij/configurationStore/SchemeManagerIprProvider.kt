/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.loadElement
import com.intellij.util.toByteArray
import org.jdom.Element
import java.io.InputStream

class SchemeManagerIprProvider(private val subStateTagName: String) : StreamProvider {
  private val nameToData = ContainerUtil.newConcurrentMap<String, ByteArray>()

  override fun read(fileSpec: String, roamingType: RoamingType): InputStream? {
    val name = PathUtilRt.getFileName(fileSpec)
    return nameToData.get(name)?.let(ByteArray::inputStream)
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) {
    nameToData.remove(PathUtilRt.getFileName(fileSpec))
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (String) -> Boolean,
                               processor: (String, InputStream, Boolean) -> Boolean) {
    for ((name, data) in nameToData) {
      if (filter(name) && !data.inputStream().use { processor(name, it, false) }) {
        break
      }
    }
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    val name = PathUtilRt.getFileName(fileSpec)
    nameToData.put(name, ArrayUtil.realloc(content, size))
  }

  fun load(state: Element?) {
    nameToData.clear()

    if (state == null) {
      return
    }

    for (profileElement in state.getChildren(subStateTagName)) {
      var name: String? = null
      for (optionElement in profileElement.getChildren("option")) {
        if (optionElement.getAttributeValue("name") == "myName") {
          name = optionElement.getAttributeValue("value")
        }
      }

      if (name.isNullOrEmpty()) {
        continue
      }

      nameToData.put("$name.xml", profileElement.toByteArray())
    }
  }

  fun writeState(state: Element) {
    val names = nameToData.keys.toTypedArray()
    names.sort()
    for (name in names) {
      nameToData.get(name)?.let { state.addContent(loadElement(it.inputStream())) }
    }
  }
}