// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.impl.TextSearchRightActionAction.CaseSensitiveAction
import com.intellij.find.impl.TextSearchRightActionAction.RegexpAction
import com.intellij.find.impl.TextSearchRightActionAction.WordAction
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.providers.SeTextQueryFilter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextQueryFilterEditor(rightActionsBoolean: List<Boolean>) :
  SeFilterEditorBase<SeTextQueryFilter>(SeTextQueryFilter(rightActionsBoolean[0],
                                                          rightActionsBoolean[1],
                                                          rightActionsBoolean[2])) {
  private val caseSensitiveAction = CaseSensitiveAction(AtomicBooleanProperty(rightActionsBoolean[0]).apply {
    afterChange { filterValue = filterValue.cloneWithCase(it) }
  }, { }, {})
  private val wordAction = WordAction(AtomicBooleanProperty(rightActionsBoolean[1]).apply {
    afterChange { filterValue = filterValue.cloneWithWords(it) }
  }, { }, { })
  private val regexpAction = RegexpAction(AtomicBooleanProperty(rightActionsBoolean[2]).apply {
    afterChange { filterValue = filterValue.cloneWithRegex(it) }
  }, { }, { })

  override fun getActions(): List<AnAction> {
    return listOf(caseSensitiveAction, wordAction, regexpAction)
  }

  fun selectCaseSensitiveAction(selected: Boolean) {
    filterValue = filterValue.cloneWithCase(selected)
    caseSensitiveAction.setSelected(createActionEvent(), selected)
  }

  fun selectWordAction(selected: Boolean) {
    filterValue = filterValue.cloneWithWords(selected)
    wordAction.setSelected(createActionEvent(), selected)
  }

  fun selectRegexpAction(selected: Boolean) {
    filterValue = filterValue.cloneWithRegex(selected)
    regexpAction.setSelected(createActionEvent(), selected)
  }

  private fun createActionEvent(): AnActionEvent {
    return AnActionEvent.createEvent(
      DataContext.EMPTY_CONTEXT,
      Presentation(),
      "SeTextQueryFilterEditor",
      ActionUiKind.NONE,
      null
    )
  }
}