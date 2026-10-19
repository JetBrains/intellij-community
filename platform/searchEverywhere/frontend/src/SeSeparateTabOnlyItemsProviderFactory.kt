// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import org.jetbrains.annotations.ApiStatus

/**
 * A marker for an items provider factory whose provider runs only in a separate tab. The All tab does not use the provider.
 */
@ApiStatus.Internal
interface SeSeparateTabOnlyItemsProviderFactory
