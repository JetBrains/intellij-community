package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposeOverrideImplementsAnnotationsFilterTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

internal class K2ComposeOverrideImplementsAnnotationsFilterTest: ComposeOverrideImplementsAnnotationsFilterTest() {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2
}