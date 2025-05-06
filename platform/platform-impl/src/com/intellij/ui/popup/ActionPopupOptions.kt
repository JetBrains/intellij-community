// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.ApiStatus

private data class ActionPopupOptionsImpl(
  val showNumbers: Boolean = false,
  val useAlphaAsNumbers: Boolean = false,
  val showDisabledActions: Boolean = false,
  val honorActionMnemonics: Boolean = false,
  val maxRowCount: Int = -1,
  val autoSelection: Boolean = false,
  val preselectCondition: Condition<in AnAction>? = null,
  val defaultIndex: Int = -1,
  val speedSearchFilter: SpeedSearchFilter<PopupFactoryImpl.ActionItem>? = null,
)

class ActionPopupOptions private constructor(
  private val options: ActionPopupOptionsImpl,
) {
  fun withSpeedSearchFilter(filter: SpeedSearchFilter<PopupFactoryImpl.ActionItem>?): ActionPopupOptions {
    return options.copy(speedSearchFilter = filter).asOption()
  }

  @ApiStatus.Internal
  fun showNumbers(): Boolean {
    return options.showNumbers
  }

  @ApiStatus.Internal
  fun useAlphaAsNumbers(): Boolean {
    return options.useAlphaAsNumbers
  }

  @ApiStatus.Internal
  fun showDisabledActions(): Boolean {
    return options.showDisabledActions
  }

  @ApiStatus.Internal
  fun honorActionMnemonics(): Boolean {
    return options.honorActionMnemonics
  }

  @ApiStatus.Internal
  fun getMaxRowCount(): Int {
    return options.maxRowCount
  }

  @ApiStatus.Internal
  fun autoSelectionEnabled(): Boolean {
    return options.autoSelection
  }

  @ApiStatus.Internal
  fun getPreselectCondition(): Condition<in AnAction>? {
    return options.preselectCondition
  }

  @ApiStatus.Internal
  fun getDefaultIndex(): Int {
    return options.defaultIndex
  }

  @ApiStatus.Internal
  fun getSpeedSearchFilter(): SpeedSearchFilter<PopupFactoryImpl.ActionItem>? {
    return options.speedSearchFilter
  }

  companion object {
    @JvmStatic
    fun empty(): ActionPopupOptions {
      return ActionPopupOptionsImpl()
        .asOption()
    }

    @JvmStatic
    fun showDisabled(): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        showDisabledActions = true,
      ).asOption()
    }

    @JvmStatic
    fun honorMnemonics(): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        honorActionMnemonics = true,
      ).asOption()
    }

    @JvmStatic
    fun mnemonicsAndDisabled(): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        showDisabledActions = true,
        honorActionMnemonics = true,
      ).asOption()
    }

    @JvmStatic
    fun forStep(
      showDisabledActions: Boolean,
      enableMnemonics: Boolean,
      autoSelection: Boolean,
      preselectCondition: Condition<in AnAction>?,
    ): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = enableMnemonics,
        autoSelection = autoSelection,
        preselectCondition = preselectCondition,
      ).asOption()
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
      return ActionPopupOptionsImpl(
        showNumbers = showNumbers,
        useAlphaAsNumbers = useAlphaAsNumbers,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = honorActionMnemonics,
        autoSelection = autoSelectionEnabled,
        preselectCondition = preselectCondition,
        defaultIndex = defaultOptionIndex
      ).asOption()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun convertForStep(
      options: ActionPopupOptions,
      items: List<PopupFactoryImpl.ActionItem>,
    ): ActionPopupOptions {
      val options = options.options
      val enableMnemonics = options.showNumbers ||
                            options.honorActionMnemonics && PopupFactoryImpl.anyMnemonicsIn(items)

      val defaultItem = items.getOrNull(options.defaultIndex)
      val preselectCondition: Condition<in AnAction>? = when {
        options.preselectCondition != null -> options.preselectCondition
        defaultItem != null -> Condition { action -> defaultItem.action == action }
        else -> null
      }

      return ActionPopupOptionsImpl(
        showDisabledActions = options.showDisabledActions,
        honorActionMnemonics = enableMnemonics,
        autoSelection = options.autoSelection,
        preselectCondition = preselectCondition,
        speedSearchFilter = options.speedSearchFilter,
      ).asOption()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun convertForSubStep(
      options: ActionPopupOptions,
    ): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        showDisabledActions = options.options.showDisabledActions,
        honorActionMnemonics = false,
        autoSelection = false,
        preselectCondition = options.options.preselectCondition,
        speedSearchFilter = null,
      ).asOption()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun forAid(
      aid: ActionSelectionAid?,
      showDisabledActions: Boolean,
      maxRowCount: Int,
      preselectCondition: Condition<in AnAction>?,
    ): ActionPopupOptions {
      return ActionPopupOptionsImpl(
        showNumbers = aid == ActionSelectionAid.ALPHA_NUMBERING || aid == ActionSelectionAid.NUMBERING,
        useAlphaAsNumbers = aid == ActionSelectionAid.ALPHA_NUMBERING,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = aid == ActionSelectionAid.MNEMONICS,
        maxRowCount = maxRowCount,
        preselectCondition = preselectCondition,
      ).asOption()
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
      return ActionPopupOptionsImpl(
        showNumbers = showNumbers,
        useAlphaAsNumbers = useAlphaAsNumbers,
        showDisabledActions = showDisabledActions,
        honorActionMnemonics = honorActionMnemonics,
        maxRowCount = maxRowCount,
        autoSelection = autoSelection,
        preselectCondition = preselectCondition,
      ).asOption()
    }

    private fun ActionPopupOptionsImpl.asOption(): ActionPopupOptions = ActionPopupOptions(this)
  }
}
