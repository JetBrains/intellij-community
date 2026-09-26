// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

/**
 * Type of the analyzing status that's taking place.
 */
enum class AnalyzingType {
  COMPLETE, // Analyzing complete, final results are available or none if OFF or in PowerSave mode
  SUSPENDED, // Analyzing suspended for long process like indexing
  PARTIAL,  // Analyzing has partial results available for displaying
  EMPTY     // Analyzing in progress but no information is available
}