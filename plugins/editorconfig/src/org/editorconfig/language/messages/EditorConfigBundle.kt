// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.messages

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE_NAME: String = "messages.EditorConfigBundle"

object EditorConfigBundle : DynamicBundle(BUNDLE_NAME) {
  const val BUNDLE = BUNDLE_NAME

  @Nls
  fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    getMessage(key, *params)

  @Suppress("RemoveRedundantSpreadOperator")
  @Nls
  operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String) = get(key, *emptyArray())

  @JvmStatic
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String) = get(key)

  @JvmStatic
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, param: String) = get(key, param)

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> = getLazyMessage(key, *params)
}