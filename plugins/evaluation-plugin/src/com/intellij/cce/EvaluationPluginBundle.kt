package com.intellij.cce

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.EvaluationPluginBundle"

object EvaluationPluginBundle : DynamicBundle(BUNDLE) {
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String,
                     vararg params: Any): java.util.function.Supplier<String> = getLazyMessage(key, *params)
}