// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

enum StatsCounters {
  totalUpdateCount,
  forceUseCached,
  forceCachedDelayedUpdateCount,
  asyncUpdateCount, 	        // total count of async updates
  asyncUpdateCancelCount,	// total count of cancels of async updates
  asyncUpdateActionsCount,	// total count of async actions (UpdateInBackgroud)

  touchbarCreationDurationNs,
  itemsCreationDurationNs,
  touchbarReleaseDurationNs,
  applyPresentaionChangesDurationNs,
  totalUpdateDurationNs,

  scrubberIconsProcessingDurationNs
}
