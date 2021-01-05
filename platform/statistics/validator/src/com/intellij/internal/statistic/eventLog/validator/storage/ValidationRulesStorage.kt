// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules

interface ValidationRulesStorage {
  fun getGroupRules(groupId: String): EventGroupRules?

  fun isUnreachable(): Boolean

  /**
   * Loads and updates events scheme from the server if necessary
   */
  fun update()

  /**
   * Re-loads events scheme from local caches
   */
  fun reload()
}