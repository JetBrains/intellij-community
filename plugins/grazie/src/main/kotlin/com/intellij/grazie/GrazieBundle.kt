// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object GrazieBundle {
  const val DEFAULT_BUNDLE_NAME = "messages.GrazieBundle"
  const val PLUGIN_BUNDLE_NAME = "messages.GraziePluginBundle"

  private val defaultBundle by lazy { DynamicBundle.INSTANCE.getResourceBundle(DEFAULT_BUNDLE_NAME, javaClass.classLoader) }
  private val pluginBundle by lazy { DynamicBundle.INSTANCE.getResourceBundle(PLUGIN_BUNDLE_NAME, javaClass.classLoader) }

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = DEFAULT_BUNDLE_NAME) key: String, vararg params: Any): String {
    val bundle = if (!GraziePlugin.isBundled && pluginBundle.containsKey(key)) pluginBundle else defaultBundle
    return AbstractBundle.message(bundle, key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = DEFAULT_BUNDLE_NAME) key: String, vararg params: Any): Supplier<String> = Supplier {
    message(key, *params)
  }
}
