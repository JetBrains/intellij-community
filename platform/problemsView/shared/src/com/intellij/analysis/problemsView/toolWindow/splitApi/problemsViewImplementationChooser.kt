package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isSplitProblemsViewKeyEnabled(): Boolean {
  return Registry.`is`("problems.view.split.enabled", defaultValue = false)
}

@ApiStatus.Internal
fun setProblemsViewImplementationForNextIdeRun(shouldEnableSplitImplementation: Boolean) {
  Registry.get("problems.view.split.enabled").setValue(shouldEnableSplitImplementation)
}

