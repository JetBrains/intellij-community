// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.messages

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE_NAME: String = "messages.EditorConfigBundle"

object EditorConfigBundle {
  internal val bundle: DynamicBundle = DynamicBundle(EditorConfigBundle::class.java, BUNDLE_NAME)

  const val BUNDLE = BUNDLE_NAME

  @Nls
  fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = bundle.getMessage(key, *params)

  @Nls
  operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String) = get(key, *emptyArray())

  @JvmStatic
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String) = get(key)

  @JvmStatic
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, param: String) = get(key, param)
}