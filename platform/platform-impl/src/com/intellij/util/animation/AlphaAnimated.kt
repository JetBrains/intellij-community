// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation

import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface AlphaAnimated {
  val alphaAnimator: ShowHideAnimator
}
