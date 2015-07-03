/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores

import com.intellij.openapi.components.RoamingType
import java.io.IOException
import java.io.InputStream

public interface StreamProvider {
  public open fun isEnabled(): Boolean = true

  /**
   * fileSpec Only main fileSpec, not version
   */
  public open fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = true

  /**
   * @param fileSpec
   * *
   * @param content bytes of content, size of array is not actual size of data, you must use `size`
   * *
   * @param size actual size of data
   */
  public fun saveContent(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType)

  public fun loadContent(fileSpec: String, roamingType: RoamingType): InputStream?

  public open fun listSubFiles(fileSpec: String, roamingType: RoamingType): Collection<String> = emptyList()

  /**
   * You must close passed input stream.
   */
  public open fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) {
    for (name in listSubFiles(path, roamingType)) {
      if (!filter(name)) {
        continue
      }

      val input: InputStream?
      try {
        input = loadContent("$path/$name", roamingType)
      }
      catch (e: IOException) {
        StorageUtil.LOG.error(e)
        continue
      }


      if (input != null && !processor(name, input, false)) {
        break
      }
    }
  }

  /**
   * Delete file or directory
   */
  public fun delete(fileSpec: String, roamingType: RoamingType)
}