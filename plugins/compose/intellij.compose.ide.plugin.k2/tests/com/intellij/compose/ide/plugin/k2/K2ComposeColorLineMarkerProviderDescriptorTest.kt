package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptor
import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptorTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import kotlin.reflect.KClass

internal class K2ComposeColorLineMarkerProviderDescriptorTest : ComposeColorLineMarkerProviderDescriptorTest() {

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

  override val expectedComposeColorLineMarkerProviderDescriptorClass: KClass<out ComposeColorLineMarkerProviderDescriptor>
    get() = K2ComposeColorLineMarkerProviderDescriptor::class
}