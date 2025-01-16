// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class OnboardingTipsInstallationInfo(val fileName: String, val offsetForBreakpoint: (CharSequence) -> Int?) {
  @Deprecated("Use constructor without simpleSampleText", ReplaceWith("OnboardingTipsInstallationInfo(fileName, offsetForBreakpoint)"))
  constructor(simpleSampleText: String, fileName: String, offsetForBreakpoint: (CharSequence) -> Int?) : this(fileName, offsetForBreakpoint)
}
