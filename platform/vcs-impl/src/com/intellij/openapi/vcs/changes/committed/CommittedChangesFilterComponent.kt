// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.ui.FilterComponent
import com.intellij.ui.LightColors
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList
import com.intellij.util.ui.UIUtil.getTextFieldBackground
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.event.ItemListener
import java.util.function.Supplier
import java.util.regex.PatternSyntaxException
import javax.swing.JComponent
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

private typealias CommittedChangeListPredicate = (CommittedChangeList) -> Boolean

private val TEXT_FILTER_KEY = CommittedChangesFilterKey("text", CommittedChangesFilterPriority.TEXT)

@ApiStatus.Internal
class CommittedChangesFilterComponent :
  FilterComponent("COMMITTED_CHANGES_FILTER_HISTORY", 20),
  ChangeListFilteringStrategy,
  Disposable {

  private val listeners = createLockFreeCopyOnWriteList<ChangeListener>()

  private val regexCheckBox = JBCheckBox(message("committed.changes.regex.title")).apply {
    model.addItemListener(ItemListener { filter() })
  }
  private val regexValidator = ComponentValidator(this)
    .withValidator(Supplier { validateRegex() })
    .installOn(textEditor)
  private var regex: Regex? = null

  init {
    add(regexCheckBox, BorderLayout.EAST)
  }

  private fun hasValidationErrors(): Boolean = regexValidator.validationInfo != null

  private fun validateRegex(): ValidationInfo? {
    if (!regexCheckBox.isSelected) return null

    regex = null
    val value = filter.takeUnless { it.isNullOrEmpty() } ?: return null

    return try {
      regex = Regex(value)
      null
    }
    catch (e: PatternSyntaxException) {
      ValidationInfo(message("changes.please.enter.a.valid.regex"), textEditor)
    }
  }

  override fun filter() {
    regexValidator.revalidate()

    val event = ChangeEvent(this)
    listeners.forEach { it.stateChanged(event) }
  }

  override fun getKey(): CommittedChangesFilterKey = TEXT_FILTER_KEY

  override fun getFilterUI(): JComponent? = null

  override fun addChangeListener(listener: ChangeListener) {
    listeners += listener
  }

  override fun removeChangeListener(listener: ChangeListener) {
    listeners -= listener
  }

  override fun setFilterBase(changeLists: List<CommittedChangeList>) = Unit

  override fun resetFilterBase() = Unit

  override fun appendFilterBase(changeLists: List<CommittedChangeList>) = Unit

  override fun filterChangeLists(changeLists: List<CommittedChangeList>): List<CommittedChangeList> {
    val result = doFilter(changeLists)
    textEditor.background = if (result.isEmpty() && changeLists.isNotEmpty()) LightColors.RED else getTextFieldBackground()
    return result
  }

  private fun doFilter(changeLists: List<CommittedChangeList>): List<CommittedChangeList> {
    if (hasValidationErrors()) return emptyList()
    val predicate = createFilterPredicate() ?: return changeLists

    return changeLists.filter(predicate)
  }

  private fun createFilterPredicate(): CommittedChangeListPredicate? =
    if (regexCheckBox.isSelected) regex?.let { RegexPredicate(it) }
    else filter.takeUnless { it.isNullOrBlank() }?.let { WordPredicate(it) }
}

private class RegexPredicate(private val regex: Regex) : CommittedChangeListPredicate {
  override fun invoke(changeList: CommittedChangeList): Boolean =
    regex.containsMatchIn(changeList.comment.orEmpty()) ||
    regex.containsMatchIn(changeList.committerName.orEmpty()) ||
    regex.containsMatchIn(changeList.number.toString())
}

private class WordPredicate(filter: String) : CommittedChangeListPredicate {
  private val filterWords = filter.split(" ")

  override fun invoke(changeList: CommittedChangeList): Boolean =
    filterWords.any { word ->
      changeList.comment.orEmpty().contains(word, true) ||
      changeList.committerName.orEmpty().contains(word, true) ||
      changeList.number.toString().contains(word)
    }
}