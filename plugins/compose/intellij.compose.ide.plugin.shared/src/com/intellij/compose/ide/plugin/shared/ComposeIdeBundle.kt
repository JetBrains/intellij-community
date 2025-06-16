package com.intellij.compose.ide.plugin.shared

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object ComposeIdeBundle {

  private const val BUNDLE: String = "messages.ComposeIdeBundle"

  private val INSTANCE: DynamicBundle = DynamicBundle(ComposeIdeBundle::class.java, BUNDLE)

  @Nls
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    INSTANCE.getMessage(key, *params)
}