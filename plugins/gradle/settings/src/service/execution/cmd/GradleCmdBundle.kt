// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.cmd

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val PATH_TO_BUNDLE = "messages.GradleCmdBundle"

internal object GradleCmdBundle : DynamicBundle(GradleCmdBundle::class.java, PATH_TO_BUNDLE) {

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> =
    getLazyMessage(key, *params)
}
