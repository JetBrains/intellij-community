// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  abstract val activeScheme: T?

  @Deprecated(replaceWith = ReplaceWith("activeScheme"), message = "Use activeScheme")
  open fun getCurrentScheme(): Scheme = activeScheme as Scheme

  /**
   * If schemes are lazy loaded, you can use this method to postpone scheme selection (scheme will be found by name on first use)
   */
  abstract var currentSchemeName: String?

  abstract val allSchemeNames: Collection<String>

  abstract val rootDirectory: File

  abstract fun loadSchemes(): Collection<T>

  open fun reload() {}

  @Deprecated("Use addScheme", ReplaceWith("addScheme(scheme, replaceExisting)"))
  fun addNewScheme(scheme: Scheme, replaceExisting: Boolean) {
    @Suppress("UNCHECKED_CAST")
    addScheme(scheme as T, replaceExisting)
  }

  fun addScheme(scheme: T) = addScheme(scheme, true)

  abstract fun addScheme(scheme: T, replaceExisting: Boolean)

  abstract fun findSchemeByName(schemeName: String): T?

  abstract fun setCurrentSchemeName(schemeName: String?, notify: Boolean)

  @JvmOverloads
  open fun setCurrent(scheme: T?, notify: Boolean = true) {
  }

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
