// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.topHit

import com.intellij.ide.IdeBundle
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProviderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeFrontendTopHitItemsProviderFactory : SeTopHitItemsProviderFactory() {
  override val isHost: Boolean get() = false
  override val displayName: @Nls String get() = IdeBundle.message("search.everywhere.group.name.top.hit")
}