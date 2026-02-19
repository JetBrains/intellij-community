// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Class that provides a constant list of possible action ids.
 *
 * Such actions usually used in places where actual action represented with lambda like [com.intellij.notification.NotificationAction].
 * If actions are registered in XML or a part of predefined allowlist it will be reported automatically.
 *
 * **Note**: when implement a new id holder,
 * the related collectors which use [ActionsEventLogGroup.ACTION_ID] explicitly or implicitly
 * (e.g., by "action_id" name) should increment theirs group version.
 *
 * @see ActionsBuiltInAllowedlist.isAllowedActionId
 * @see ActionIdProvider
 */
@Internal
interface ActionIdsHolder {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<ActionIdsHolder>("com.intellij.statistics.actionIdsHolder");
  }

  /**
   * @see ActionIdProvider
   */
  fun getActionsIds(): List<String>
}
