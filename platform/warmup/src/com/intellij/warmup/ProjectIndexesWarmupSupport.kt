package com.intellij.warmup


import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Allows to add a custom 'Indexes are built' event for warmup mode.
 * All such events are awaited in warmup.
 */
@ApiStatus.Internal
interface ProjectIndexesWarmupSupport {
  companion object {
    var EP_NAME = ExtensionPointName<ProjectIndexesWarmupSupport>("com.intellij.projectIndexesWarmupSupport")
  }

  /**
   * Return a future which is completed only when a custom index is ready
   */
  fun warmAdditionalIndexes(): CompletableFuture<Unit>
}