package org.jetbrains.completion.full.line.settings

import com.intellij.AbstractBundle
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey

class MLServerCompletionBundle private constructor() : AbstractBundle(BUNDLE) {
  companion object {
    const val BUNDLE = "messages.MLServerCompletionBundle"

    fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, @NotNull vararg params: Any): String {
      return instance.getMessage(key, *params)
    }

    private val instance = MLServerCompletionBundle()
  }
}
