// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.ApiStatus

class ActionPopupOptions private constructor(
  private val showNumbers: Boolean,
  private val useAlphaAsNumbers: Boolean,
  private val showDisabledActions: Boolean,
  private val honorActionMnemonics: Boolean,
  private val maxRowCount: Int,
  private val autoSelection: Boolean,
  private val preselectCondition: Condition<in AnAction>?,
  private val defaultIndex: Int,
) {
  @ApiStatus.Internal
  fun showNumbers(): Boolean {
    return showNumbers
  }

  @ApiStatus.Internal
  fun useAlphaAsNumbers(): Boolean {
    return useAlphaAsNumbers
  }

  @ApiStatus.Internal
  fun showDisabledActions(): Boolean {
    return showDisabledActions
  }

  @ApiStatus.Internal
  fun honorActionMnemonics(): Boolean {
    return honorActionMnemonics
  }

  @ApiStatus.Internal
  fun getMaxRowCount(): Int {
    return maxRowCount
  }

  @ApiStatus.Internal
  fun autoSelectionEnabled(): Boolean {
    return autoSelection
  }

  @ApiStatus.Internal
  fun getPreselectCondition(): Condition<in AnAction>? {
    return preselectCondition
  }

  @ApiStatus.Internal
  fun getDefaultIndex(): Int {
    return defaultIndex
  }

  companion object {
    @JvmStatic
    fun empty(): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = false,
        useAlphaAsNumbers = false,
        showDisabledActions = false,
        honorActionMnemonics = false,
        maxRowCount = -1,
        autoSelection = false,
        preselectCondition = null,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun showDisabled(): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = false,
        useAlphaAsNumbers = false,
        showDisabledActions = true,
        honorActionMnemonics = false,
        maxRowCount = -1,
        autoSelection = false,
        preselectCondition = null,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun honorMnemonics(): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = false,
        useAlphaAsNumbers = false,
        showDisabledActions = false,
        honorActionMnemonics = true,
        maxRowCount = -1,
        autoSelection = false,
        preselectCondition = null,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun mnemonicsAndDisabled(): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = false,
        useAlphaAsNumbers = false,
        showDisabledActions = true,
        honorActionMnemonics = true,
        maxRowCount = -1,
        autoSelection = false,
        preselectCondition = null,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun forStep(
      showDisabledActions: Boolean,
      enableMnemonics: Boolean,
      autoSelection: Boolean,
      preselectCondition: Condition<in AnAction>?,
    ): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = false,
        useAlphaAsNumbers = false,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = enableMnemonics,
        maxRowCount = -1,
        autoSelection = autoSelection,
        preselectCondition = preselectCondition,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun forStepAndItems(
      showNumbers: Boolean,
      useAlphaAsNumbers: Boolean,
      showDisabledActions: Boolean,
      honorActionMnemonics: Boolean,
      autoSelectionEnabled: Boolean,
      preselectCondition: Condition<in AnAction>?,
      defaultOptionIndex: Int,
    ): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = showNumbers,
        useAlphaAsNumbers = useAlphaAsNumbers,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = honorActionMnemonics,
        maxRowCount = -1,
        autoSelection = autoSelectionEnabled,
        preselectCondition = preselectCondition,
        defaultIndex = defaultOptionIndex
      )
    }

    @ApiStatus.Internal
    @JvmStatic
    fun forAid(
      aid: ActionSelectionAid?,
      showDisabledActions: Boolean,
      maxRowCount: Int,
      preselectCondition: Condition<in AnAction>?,
    ): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = aid == ActionSelectionAid.ALPHA_NUMBERING || aid == ActionSelectionAid.NUMBERING,
        useAlphaAsNumbers = aid == ActionSelectionAid.ALPHA_NUMBERING,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = aid == ActionSelectionAid.MNEMONICS,
        maxRowCount = maxRowCount,
        autoSelection = false,
        preselectCondition = preselectCondition,
        defaultIndex = -1
      )
    }

    @JvmStatic
    fun create(
      showNumbers: Boolean,
      useAlphaAsNumbers: Boolean,
      showDisabledActions: Boolean,
      honorActionMnemonics: Boolean,
      maxRowCount: Int,
      autoSelection: Boolean,
      preselectCondition: Condition<in AnAction>?,
    ): ActionPopupOptions {
      return ActionPopupOptions(
        showNumbers = showNumbers,
        useAlphaAsNumbers = useAlphaAsNumbers,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = honorActionMnemonics,
        maxRowCount = maxRowCount,
        autoSelection = autoSelection,
        preselectCondition = preselectCondition,
        defaultIndex = -1
      )
    }
  }
}
