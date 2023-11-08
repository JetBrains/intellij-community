package com.intellij.ide.startup.importSettings

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val PATH_TO_BUNDLE = "messages.ImportSettingsBundle"

@ApiStatus.Internal
object ImportSettingsBundle {
  private val bundle = DynamicBundle(ImportSettingsBundle::class.java, PATH_TO_BUNDLE)
  fun message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any) : @Nls String {
    return bundle.getMessage(key, *params)
  }
}
