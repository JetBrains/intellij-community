// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object GrazieBundle {
  const val DEFAULT_BUNDLE_NAME = "messages.GrazieBundle"
  private const val PLUGIN_BUNDLE_NAME = "messages.GraziePluginBundle"

  private val defaultBundle by lazy { DynamicBundle.getResourceBundle(javaClass.classLoader, DEFAULT_BUNDLE_NAME) }
  private val pluginBundle by lazy { DynamicBundle.getResourceBundle(javaClass.classLoader, PLUGIN_BUNDLE_NAME) }

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = DEFAULT_BUNDLE_NAME) key: String, vararg params: Any): String {
    val bundle = if (!GraziePlugin.isBundled && pluginBundle.containsKey(key)) pluginBundle else defaultBundle
    return AbstractBundle.message(bundle, key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = DEFAULT_BUNDLE_NAME) key: String, vararg params: Any): Supplier<String> {
    val actualParams = if (params.isEmpty()) ArrayUtil.EMPTY_OBJECT_ARRAY else params
    return Supplier { message(key, *actualParams) }
  }

  fun messageOrNull(@PropertyKey(resourceBundle = DEFAULT_BUNDLE_NAME) key: String, vararg params: String): @Nls String? {
    val bundle = if (!GraziePlugin.isBundled && pluginBundle.containsKey(key)) pluginBundle else defaultBundle
    return AbstractBundle.messageOrNull(bundle, key, *params)
  }
}
