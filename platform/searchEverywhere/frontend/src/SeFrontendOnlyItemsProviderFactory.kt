// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import org.jetbrains.annotations.ApiStatus

// Marker interface for items providers that should be initialized only on the frontend even if they are available on the backend
@ApiStatus.Internal
interface SeFrontendOnlyItemsProviderFactory