// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for providing preview information for search items
 *
 * See [SeItemsProvider], [SePreviewInfoFactory]
 */
@ApiStatus.Experimental
interface SeItemsPreviewProvider {
  suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo?
}