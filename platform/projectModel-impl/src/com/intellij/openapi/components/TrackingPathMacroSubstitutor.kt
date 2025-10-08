// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface TrackingPathMacroSubstitutor : PathMacroSubstitutor {
  fun getUnknownMacros(componentName: String?): Set<String>

  fun getComponents(macros: Collection<String>): Set<String>

  fun addUnknownMacros(componentName: String, unknownMacros: Collection<String>)

  fun invalidateUnknownMacros(macros: Set<String>)

  fun reset()
}
