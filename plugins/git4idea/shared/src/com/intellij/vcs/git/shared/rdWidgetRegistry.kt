package com.intellij.vcs.git.shared

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Registry.Companion.isRdBranchWidgetEnabled(): Boolean =
  `is`("git.branches.widget.rd", true) && !isCodeWithMe()

internal fun isCodeWithMe(): Boolean {
  val frontendType = FrontendApplicationInfo.getFrontendType() as? FrontendType.Remote ?: return false
  return frontendType.isGuest()
}