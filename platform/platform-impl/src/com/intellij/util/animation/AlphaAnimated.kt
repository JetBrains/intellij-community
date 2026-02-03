// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
@ApiStatus.Internal
internal interface AlphaAnimated {
  val alphaContext: AlphaAnimationContext
}
