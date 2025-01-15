package org.jetbrains.idea.devkit.fleet

import com.intellij.DynamicBundle
import com.intellij.codeInspection.util.IntentionName
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE_FQN: @NonNls String = "messages.DevKitFleetBundle"

object DevKitFleetBundle {
  private val BUNDLE = DynamicBundle(DevKitFleetBundle::class.java, BUNDLE_FQN)

  @Nls
  fun message(
    @NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String,
    vararg params: Any,
  ): String = BUNDLE.getMessage(key, *params)

  fun messagePointer(
    @NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String,
    vararg params: Any,
  ): Supplier<String> = BUNDLE.getLazyMessage(key, *params)
}