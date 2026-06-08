// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptor
import com.intellij.compose.ide.plugin.shared.ComposeColorLineMarkerProviderDescriptorTest
import kotlin.reflect.KClass

internal class K2ComposeColorLineMarkerProviderDescriptorTest : ComposeColorLineMarkerProviderDescriptorTest() {

  override val expectedComposeColorLineMarkerProviderDescriptorClass: KClass<out ComposeColorLineMarkerProviderDescriptor>
    get() = K2ComposeColorLineMarkerProviderDescriptor::class
}