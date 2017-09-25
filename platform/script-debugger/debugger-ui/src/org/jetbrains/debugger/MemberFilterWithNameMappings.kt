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
package org.jetbrains.debugger

open class MemberFilterWithNameMappings(private val rawNameToSource: Map<String, String> = emptyMap()) : MemberFilter {
  override fun hasNameMappings() = !rawNameToSource.isEmpty()

  override fun rawNameToSource(variable: Variable): String {
    val name = variable.name
    val sourceName = rawNameToSource.get(name)
    return sourceName ?: normalizeMemberName(name)
  }

  protected open fun normalizeMemberName(name: String) = name

  override fun sourceNameToRaw(name: String): String? {
    if (!hasNameMappings()) {
      return null
    }

    for ((key, value) in rawNameToSource) {
      if (value == name) {
        return key
      }
    }
    return null
  }
}
