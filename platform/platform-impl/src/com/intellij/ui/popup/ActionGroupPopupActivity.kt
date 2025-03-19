// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.ide.IdeEventQueue
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import org.jetbrains.annotations.NonNls
import java.awt.event.InputEvent

internal class ActionGroupPopupActivity(
  project: Project?,
  private val actionGroupId: @NonNls String?,
  private var place: String,
) {
  companion object {
    @JvmStatic fun start(popup: AbstractPopup, actionGroup: ActionGroup, place: String) {
      val component = popup.component ?: return
      val activity = ActionGroupPopupActivity(popup.project, ActionManager.getInstance().getId(actionGroup), place)
      ClientProperty.put(component, KEY, activity)
    }

    @JvmStatic fun stop(popup: AbstractPopup, ok: Boolean) {
      val currentActivity = getCurrentActivity(popup) ?: return
      currentActivity.stop(ok)
      val component = popup.component ?: return
      ClientProperty.remove(component, KEY)
    }

    @JvmStatic fun getCurrentActivity(popup: AbstractPopup): ActionGroupPopupActivity? {
      val component = popup.component ?: return null
      return ClientProperty.get(component, KEY)
    }
  }

  private val activity = ActionGroupPopupCollector.POPUP_ACTIVITY.started(project) {
    listOf(
      ActionGroupPopupCollector.ACTION_GROUP_ID.with(actionGroupId),
      ActionGroupPopupCollector.INPUT_EVENT.with(currentInputEvent(place)),
      ActionGroupPopupCollector.PLACE.with(place),
    )
  }

  private fun currentInputEvent(place: String): FusInputEvent =
    FusInputEvent(IdeEventQueue.getInstance().trueCurrentEvent as? InputEvent?, place)

  private var selectedActionId: String? = null

  fun filtered(filterLength: Int, itemsBefore: Int, itemsAfter: Int) {
    activity.stageStarted(ActionGroupPopupCollector.FILTERED) {
      listOf(
        ActionGroupPopupCollector.ACTION_GROUP_ID.with(actionGroupId),
        ActionGroupPopupCollector.PLACE.with(place),
        ActionGroupPopupCollector.FILTER_LENGTH.with(filterLength),
        ActionGroupPopupCollector.FILTER_ITEMS_BEFORE.with(itemsBefore),
        ActionGroupPopupCollector.FILTER_ITEMS_AFTER.with(itemsAfter),
      )
    }
  }

  fun itemSelected(action: AnAction, place: String?) {
    if (place != null) {
      this.place = place
    }
    val id = ActionManager.getInstance().getId(action)
    if (id != null) {
      selectedActionId = id
    }
  }

  fun stop(ok: Boolean) {
    activity.finished {
      listOf(
        ActionGroupPopupCollector.ACTION_GROUP_ID.with(actionGroupId),
        ActionGroupPopupCollector.ACTION_ID.with(selectedActionId),
        ActionGroupPopupCollector.INPUT_EVENT.with(currentInputEvent(place)),
        ActionGroupPopupCollector.PLACE.with(place),
        ActionGroupPopupCollector.OK.with(ok),
      )
    }
  }
}

private val KEY = Key.create<ActionGroupPopupActivity>("ActionGroupActivity")

internal object ActionGroupPopupCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup? = GROUP

  val GROUP = EventLogGroup("action.group.popup", 2)

  val INPUT_EVENT = EventFields.InputEvent
  val ACTION_GROUP_ID = EventFields.StringValidatedByCustomRule("action_group_id", ActionRuleValidator::class.java)
  val FILTER_LENGTH = EventFields.Int("filter_length")
  val FILTER_ITEMS_BEFORE = EventFields.Int("item_count_before")
  val FILTER_ITEMS_AFTER = EventFields.Int("item_count_after")
  val ACTION_ID = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  val OK = EventFields.Boolean("ok")
  val PLACE = EventFields.ActionPlace

  val POPUP_ACTIVITY = GROUP.registerIdeActivity(
    "show",
    startEventAdditionalFields = arrayOf(ACTION_GROUP_ID, INPUT_EVENT, PLACE),
    finishEventAdditionalFields = arrayOf(ACTION_GROUP_ID, ACTION_ID, INPUT_EVENT, OK, PLACE),
  )
  val FILTERED = POPUP_ACTIVITY.registerStage("filtered", arrayOf(ACTION_GROUP_ID, PLACE, FILTER_LENGTH, FILTER_ITEMS_BEFORE, FILTER_ITEMS_AFTER))
}
