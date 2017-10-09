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
package com.intellij.openapi.options

import com.intellij.openapi.util.Condition

import java.io.File

abstract class SchemeManager<T> {
  companion object {
    @JvmField
    val EDITABLE_COPY_PREFIX = "_@user_"

    @JvmStatic
    fun getDisplayName(scheme: Scheme): String {
      val schemeName = scheme.name
      return if (schemeName.startsWith(EDITABLE_COPY_PREFIX))
        schemeName.substring(EDITABLE_COPY_PREFIX.length)
      else
        schemeName
    }
  }

  abstract val allSchemes: List<T>

  open val isEmpty: Boolean
    get() = allSchemes.isEmpty()

  var currentScheme: T? = null
    protected set

  /**
   * If schemes are lazy loaded, you can use this method to postpone scheme selection (scheme will be found by name on first use)
   */
  abstract var currentSchemeName: String?

  abstract val allSchemeNames: Collection<String>

  abstract val rootDirectory: File

  abstract fun loadSchemes(): Collection<T>

  open fun reload() {}

  abstract fun addNewScheme(scheme: T, replaceExisting: Boolean)

  fun addScheme(scheme: T) {
    addNewScheme(scheme, true)
  }

  /**
   * Consider to use [.setSchemes]
   */
  abstract fun clearAllSchemes()

  abstract fun findSchemeByName(schemeName: String): T?

  abstract fun setCurrentSchemeName(schemeName: String?, notify: Boolean)

  fun setCurrent(scheme: T?) {
    setCurrent(scheme, true)
  }

  abstract fun setCurrent(scheme: T?, notify: Boolean)

  abstract fun removeScheme(scheme: T): Boolean

  open fun removeScheme(name: String): T? {
    val scheme = findSchemeByName(name)
    if (scheme != null) {
      removeScheme(scheme)
      return scheme
    }
    return null
  }

  /**
   * Must be called before [.loadSchemes].
   *
   * Scheme manager processor must be LazySchemeProcessor
   */
  open fun loadBundledScheme(resourceName: String, requestor: Any) {}

  @JvmOverloads
  open fun setSchemes(newSchemes: List<T>, newCurrentScheme: T? = null, removeCondition: Condition<T>? = null) {
  }

  /**
   * Bundled / read-only (or overriding) scheme cannot be renamed or deleted.
   */
  open fun isMetadataEditable(scheme: T): Boolean {
    return true
  }

  open fun save(errors: MutableList<Throwable>) {}
}
