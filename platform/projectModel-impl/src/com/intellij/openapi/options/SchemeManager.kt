// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.function.Predicate

abstract class SchemeManager<T> {
  abstract val allSchemes: List<T>

  open val isEmpty: Boolean
    get() = allSchemes.isEmpty()

  abstract val activeScheme: T?

  /**
   * If schemes are lazy loaded, you can use this method to postpone scheme selection (scheme will be found by name on first use)
   */
  abstract var currentSchemeName: String?

  abstract val allSchemeNames: Collection<String>

  abstract val rootDirectory: File

  abstract fun loadSchemes(): Collection<T>

  abstract fun reload()

  @Deprecated("Use addScheme", ReplaceWith("addScheme(scheme, replaceExisting)"))
  @ApiStatus.ScheduledForRemoval
  fun addNewScheme(scheme: Scheme, replaceExisting: Boolean) {
    @Suppress("UNCHECKED_CAST")
    addScheme(scheme as T, replaceExisting)
  }

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
  abstract fun loadBundledScheme(resourceName: String, requestor: Any?, pluginDescriptor: PluginDescriptor?)

  @JvmOverloads
  open fun setSchemes(newSchemes: List<T>, newCurrentScheme: T? = null, removeCondition: Predicate<T>? = null) {
  }

  /**
   * Bundled / read-only (or overriding) scheme cannot be renamed or deleted.
   */
  abstract fun isMetadataEditable(scheme: T): Boolean

  abstract fun save(errors: MutableList<Throwable>)
}
