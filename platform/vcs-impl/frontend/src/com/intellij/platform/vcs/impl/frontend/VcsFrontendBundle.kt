// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.vcs.impl.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

internal object VcsFrontendBundle {
  const val BUNDLE: @NonNls String = "messages.VcsFrontendBundle"
  val INSTANCE: DynamicBundle = DynamicBundle(VcsFrontendBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(
    key: @PropertyKey(resourceBundle = BUNDLE) String,
    vararg params: Any,
  ): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}