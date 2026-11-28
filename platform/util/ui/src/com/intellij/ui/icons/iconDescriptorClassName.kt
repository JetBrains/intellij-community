// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.annotations.ApiStatus

/**
 * Gives the simple name of the generated icon descriptor class for the simple name of an icon class.
 * The icon class generator names its descriptor classes with this function, and the action manager finds them with it.
 *
 * - A name that contains `Icons` gets `IconDescriptors` in its place: `AllIcons` becomes `AllIconDescriptors`.
 * - Another name loses a final `s` and gets `Descriptors`: `StudioIllustrations` becomes `StudioIllustrationDescriptors`.
 */
@ApiStatus.Internal
fun iconDescriptorClassName(iconClassName: String): String {
  return if (iconClassName.contains("Icons")) {
    iconClassName.replace("Icons", "IconDescriptors")
  }
  else {
    iconClassName.removeSuffix("s") + "Descriptors"
  }
}
