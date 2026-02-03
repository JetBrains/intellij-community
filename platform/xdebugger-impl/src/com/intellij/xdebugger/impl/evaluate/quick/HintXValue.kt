// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for [XValue] which is used in [XValueHint].
 * The main purpose of this class is disposing of [XValue] calculated for the hint, when the hint is hidden.
 */
@ApiStatus.Internal
interface HintXValue : Disposable