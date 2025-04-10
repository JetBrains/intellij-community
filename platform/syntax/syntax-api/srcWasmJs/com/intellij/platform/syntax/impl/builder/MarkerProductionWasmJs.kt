// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import fleet.util.multiplatform.Actual

@Suppress("unused")
@Actual("doHeavyCheckImpl")
internal fun doHeavyCheckImplWasmJs(
  production: MarkerProduction,
  doneMarker: CompositeMarker,
  anchorBefore: CompositeMarker?,
) {
  // TODO no check here yet
}
