// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SearchEverywhereFrontendBundle {
  val bundle: DynamicBundle = DynamicBundle(SearchEverywhereFrontendBundle::class.java, "messages.searchEverywhereFrontendBundle")
}
