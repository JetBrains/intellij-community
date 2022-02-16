package com.intellij.ui.content.custom.options

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import java.util.*

abstract class PersistentContentCustomLayoutOptions(private val content: Content,
                                                    private val selectedOptionKey: String) : CustomContentLayoutOptions {

  companion object {
    const val HIDE_OPTION_KEY = "Hidden" //TODO check that none of the options have the same key
  }

  override fun select(option: CustomContentLayoutOption) {
    option as? PersistentContentCustomLayoutOption ?: throw IllegalStateException(
      "Option is not a ${PersistentContentCustomLayoutOption::class.java.name}")
    doSelect(option)
    saveOption(option.getOptionKey())
  }

  override fun isSelected(option: CustomContentLayoutOption): Boolean = option.isSelected

  override fun restore() {
    val defaultOption = getDefaultOption()
    if (!defaultOption.isSelected) {
      select(defaultOption)
    }
  }

  fun isContentVisible(): Boolean {
    return content.isValid && (content.manager?.getIndexOfContent(content) ?: -1) != -1
  }

  fun getCurrentOption(): PersistentContentCustomLayoutOption? {
    val currentOptionKey = getCurrentOptionKey()
    if (currentOptionKey == HIDE_OPTION_KEY) {
      return null
    }
    return getPersistentOptions().first { it.getOptionKey() == getCurrentOptionKey() }
  }

  protected abstract fun doSelect(option: CustomContentLayoutOption)

  protected abstract fun getDefaultOptionKey(): String
  private fun getCurrentOptionKey() = PropertiesComponent.getInstance().getValue(selectedOptionKey) ?: getDefaultOptionKey()

  private fun getDefaultOption() = getPersistentOptions().first { it.getOptionKey() == getDefaultOptionKey() }

  private fun saveOption(optionKey: @NlsSafe String) {
    PropertiesComponent.getInstance().setValue(selectedOptionKey, optionKey)
  }

  private fun getPersistentOptions() = availableOptions.filterIsInstance<PersistentContentCustomLayoutOption>()

  override fun onHide() {
    saveOption(HIDE_OPTION_KEY)
  }

  override fun getDisplayName(): String = content.displayName

  override fun isHidden(): Boolean = getCurrentOption() == null

  override fun isHideOptionVisible(): Boolean {
    if (isHidden) {
      return true
    }

    val contentManager: ContentManager = content.manager ?: return false
    return contentManager.contents.size > 1
  }
}

abstract class PersistentContentCustomLayoutOption(private val options: PersistentContentCustomLayoutOptions) : CustomContentLayoutOption {

  override fun isEnabled(): Boolean = true

  override fun isSelected(): Boolean = options.isContentVisible() && isThisOptionSelected()

  override fun select() = options.select(this)

  abstract fun getOptionKey(): @NlsSafe String

  protected fun isThisOptionSelected(): Boolean = this == options.getCurrentOption()
}