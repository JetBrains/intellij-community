// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.messages

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object EditorConfigBundle {

  const val BUNDLE: @NonNls String = "messages.EditorConfigBundle"

  private val instance: DynamicBundle = DynamicBundle(EditorConfigBundle::class.java, BUNDLE)

  operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String {
    return instance.getMessage(key, *params)
  }

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String): @Nls String {
    return get(key)
  }

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, param: String): @Nls String {
    return get(key, param)
  }

  fun messageOrDefault(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): @Nls String? {
    return instance.messageOrDefault(key, defaultValue = null, params)
  }
}
