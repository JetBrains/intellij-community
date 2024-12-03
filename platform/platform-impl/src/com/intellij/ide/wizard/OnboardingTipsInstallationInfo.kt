// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OnboardingTipsInstallationFileInfo internal constructor(
  val fileName: String,
  val offsetsForBreakpoint: (CharSequence) -> List<Int>,
)

// better to make a method constructor, so it would be easier to change the onboarding information in the future
@ApiStatus.Internal
fun onboardingTipsWithCommentsAndBreakpoints(fileName: String, offsetsForBreakpoint: (CharSequence) -> List<Int>): OnboardingTipsInstallationFileInfo =
  OnboardingTipsInstallationFileInfo(fileName, offsetsForBreakpoint)

@ApiStatus.Internal
data class OnboardingTipsInstallationInfo(val infos: List<OnboardingTipsInstallationFileInfo>) {
  constructor(fileName: String, offsetForBreakpoint: (CharSequence) -> Int?) : this(listOf(
    onboardingTipsWithCommentsAndBreakpoints(fileName) { listOfNotNull(offsetForBreakpoint(it)) }
  ))

  @Deprecated("Use constructor without simpleSampleText", ReplaceWith("OnboardingTipsInstallationInfo(fileName, offsetForBreakpoint)"))
  constructor(simpleSampleText: String, fileName: String, offsetForBreakpoint: (CharSequence) -> Int?) : this(fileName, offsetForBreakpoint)
}
