// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemId
import com.intellij.platform.searchEverywhere.SearchEverywhereItemPresentation
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SearchEverywhereItemDataImpl(override val itemId: SearchEverywhereItemId,
                                   override val providerId: SearchEverywhereProviderId,
                                   override val weight: Int,
                                   override val presentation: SearchEverywhereItemPresentation) : SearchEverywhereItemData