package com.intellij.remoteDev

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.RemoteDevUtilBundle"

object RemoteDevUtilBundle : DynamicBundle(BUNDLE) {
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return RemoteDevUtilBundle.getMessage(key, *params)
  }
}