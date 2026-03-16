// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

/**
 * An interface to mark an implementation of [SeItemsProvider] in case the extended info should be expected for the items.
 *
 * See [SeExtendedInfo].
 */
@ApiStatus.Experimental
interface SeExtendedInfoProvider