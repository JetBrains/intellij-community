// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use TextProblem instead")
class Typo {
  /**
   * A grammar typo category
   *
   * All typos have categories that can be found in the Grazie plugin UI tree in settings/preferences.
   */
  @Deprecated("Use RuleGroup instead")
  @ApiStatus.ScheduledForRemoval
  enum class Category
}
