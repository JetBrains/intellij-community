// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls

/**
 * A diff request processor that shows one change out of many.
 */
@ApiStatus.Internal
abstract class UpdatableMultipleChangesDiffRequestProcessor(
  project: Project?,
  place: String,
) : CacheDiffRequestProcessor.Simple(project, place), DiffPreviewUpdateProcessor {
  /**
   * The presentable name of the change that the processor shows now, or `null` if it shows no change.
   * The diff editor tab uses this name as its title.
   *
   * An implementation must publish the name safely. The editor tab title provider reads it off the EDT.
   * A caller outside the EDT can get a stale name, because the shown change can change right after the read.
   */
  @CalledInAny
  abstract fun getCurrentChangeName(): @Nls String?

  /**
   * The 0-based position of the change that the processor shows now among the changes that the diff toolbar reaches.
   * The value is `-1` if the position is unknown.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  abstract fun getCurrentChangeIndex(): Int
}
