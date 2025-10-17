// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface SeSelectionResult

@Internal
class SeSelectionResultClose : SeSelectionResult
@Internal
class SeSelectionResultKeep : SeSelectionResult
@Internal
class SeSelectionResultText(val searchText: String) : SeSelectionResult