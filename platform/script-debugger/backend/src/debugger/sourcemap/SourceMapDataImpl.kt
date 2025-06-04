// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class SourceMapDataImpl(
  override val file: String?,
  override val sources: List<String>,
  override val sourcesContent: List<String?>?,
  override val hasNameMappings: Boolean,
  override val mappings: List<MappingEntry>,
  override val ignoreList: List<Int> = emptyList(),
) : SourceMapData