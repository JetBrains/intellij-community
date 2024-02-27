// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConvertObjectToDataObject")
package com.intellij.platform.settings

import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * See [SettingDescriptor.tags].
 */
interface SettingTag

/**
 * Adapter to get setting value from [com.intellij.ide.util.PropertiesComponent] if not defined.
 */
class PropertyManagerAdapterTag(val oldKey: String) : SettingTag

// Implementation note: stored in a config dir rather in a system dir (see CacheStateTag)
object NonShareableTag : SettingTag {
  override fun toString(): String = this::class.java.simpleName
}

/**
 * This is a hint indicating that the setting is non-shareable (meaning it's stored only on the local machine).
 * It functions more like a state than a setting (isn't manageable by a user).
 * This concept is used for storing certain flags, such as "dialog was shown".
 */
object NonShareableInternalTag : SettingTag {
  override fun toString(): String = this::class.java.simpleName
}

/**
 * Similar to [NonShareableInternalTag], but acts as a cache-like state. It isn't crucial if the stored value is lost frequently.
 */
// Implementation note: stored in a system dir (see NonShareableStateTag)
object CacheTag : SettingTag {
  override fun toString(): String = this::class.java.simpleName
}

@Internal
class PersistenceStateComponentPropertyTag(val componentName: String) : SettingTag {
  override fun toString(): String = "PersistenceStateComponentPropertyTag(componentName=$componentName)"
}

// for now, is supported only for PSC with Element state class
@Internal
class OldLocalValueSupplierTag(private val supplier: Lazy<JsonElement?>) : SettingTag {
  val value: JsonElement?
    get() = supplier.value

  override fun toString(): String = "OldLocalValueSupplierTag"
}
