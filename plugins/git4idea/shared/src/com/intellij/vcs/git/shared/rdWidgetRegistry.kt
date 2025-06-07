package com.intellij.vcs.git.shared

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Registry.Companion.isRdBranchWidgetEnabled(): Boolean =
  `is`("git.branches.widget.rd", false)