// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class InspectionsState {
  DISABLED,
  SHALLOW_ANALYSIS_COMPLETE,
  ESSENTIAL_ANALYSIS_COMPLETE,
  NO_PROBLEMS_FOUND,
  PERFORMING_CODE_ANALYSIS,
  OFF,
  PAUSED,
  INDEXING,
  ANALYZING,
  UNKNOWN
}