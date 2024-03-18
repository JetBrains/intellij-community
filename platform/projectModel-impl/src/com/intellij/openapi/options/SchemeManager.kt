// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.function.Predicate

abstract class SchemeManager<T> {
  abstract val allSchemes: List<T>

  open val isEmpty: Boolean
    get() = allSchemes.isEmpty()

  abstract val activeScheme: T?

  /**
   * If the schemes are lazily loaded, you can utilize this method to delay scheme selection.
   * The scheme will then be located by its name upon the first use.
   */
  abstract var currentSchemeName: String?

  abstract val allSchemeNames: Collection<String>

  abstract val rootDirectory: File

  abstract fun loadSchemes(): Collection<T>

  fun reload() {
    reload(retainFilter = null)
  }

  abstract fun reload(retainFilter: ((scheme: T) -> Boolean)?)

  fun addScheme(scheme: T) {
    addScheme(scheme, true)
  }

  abstract fun addScheme(scheme: T, replaceExisting: Boolean)

  abstract fun findSchemeByName(schemeName: String): T?

  abstract fun setCurrentSchemeName(schemeName: String?, notify: Boolean)

  @JvmOverloads
  open fun setCurrent(scheme: T?, notify: Boolean = true, processChangeSynchronously: Boolean = false) {
  }

  abstract fun removeScheme(scheme: T): Boolean

  abstract fun removeScheme(name: String): T?

  /**
   * Must be called before [.loadSchemes].
   *
   * Scheme manager processor must be LazySchemeProcessor
   */
  abstract fun loadBundledScheme(resourceName: String, requestor: Any?, pluginDescriptor: PluginDescriptor?): T?

  interface LoadBundleSchemeRequest<T> {
    val pluginId: PluginId

    val schemeKey: String

    fun loadBytes(): ByteArray

    fun createScheme(): T
  }

  abstract fun loadBundledSchemes(providers: Sequence<LoadBundleSchemeRequest<T>>)

  @JvmOverloads
  open fun setSchemes(newSchemes: List<T>, newCurrentScheme: T? = null, removeCondition: Predicate<T>? = null) {
  }

  /**
   * Bundled / read-only (or overriding) scheme cannot be renamed or deleted.
   */
  abstract fun isMetadataEditable(scheme: T): Boolean

  abstract fun save()

  /**
   * Returns the category which settings of this scheme belong to.
   */
  abstract fun getSettingsCategory(): SettingsCategory
}
