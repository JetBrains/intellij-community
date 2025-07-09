package com.intellij.vcs.git.shared

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun FrontendApplicationInfo.isCodeWithMe(): Boolean {
  val frontendType = getFrontendType() as? FrontendType.Remote ?: return false
  return frontendType.isGuest()
}