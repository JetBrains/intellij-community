// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.vcs.ex.LineStatusTrackerI
import com.intellij.openapi.vcs.ex.LineStatusTrackerListener
import com.intellij.openapi.vcs.ex.Range

/**
 * Listens to [lineStatusTracker] and sends updated ranges to [onRangesChanged]
 */
internal class LineStatusTrackerRangesHandler private constructor(
  private val lineStatusTracker: LineStatusTrackerI<*>,
  private val onRangesChanged: (List<Range>) -> Unit
) : LineStatusTrackerListener {
  init {
    val ranges = lineStatusTracker.getRanges()
    onRangesChanged(ranges.orEmpty())
  }

  override fun onOperationalStatusChange() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
    }
  }

  override fun onRangesChanged() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
    }
  }

  companion object {
    fun install(disposable: Disposable, lineStatusTracker: LineStatusTrackerI<*>, onRangesChanged: (lstRanges: List<Range>) -> Unit) {
      val listener = LineStatusTrackerRangesHandler(lineStatusTracker, onRangesChanged)
      lineStatusTracker.addListener(listener)
      disposable.whenDisposed {
        lineStatusTracker.removeListener(listener)
      }
    }
  }
}