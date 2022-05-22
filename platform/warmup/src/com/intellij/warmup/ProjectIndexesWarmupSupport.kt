package com.intellij.warmup


import com.intellij.openapi.extensions.ExtensionPointName
import java.util.concurrent.CompletableFuture

interface ProjectIndexesWarmupSupport {
  companion object {
    var EP_NAME = ExtensionPointName<ProjectIndexesWarmupSupport>("com.intellij.projectIndexesWarmupSupport")
  }
  fun warmAdditionalIndexes(): CompletableFuture<Unit>
}