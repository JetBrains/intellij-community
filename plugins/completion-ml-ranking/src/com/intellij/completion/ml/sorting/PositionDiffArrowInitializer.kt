// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.ui.IconManager
import com.intellij.ui.icons.RowIcon
import com.intellij.util.IconUtil
import icons.CompletionMlRankingIcons
import java.awt.Component
import java.awt.Graphics
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

class PositionDiffArrowInitializer : ProjectManagerListener {
  companion object {
    val POSITION_DIFF_KEY = Key.create<AtomicInteger>("PositionDiffArrowInitializer.POSITION_DIFF_KEY")
    val POSITION_CHANGED_KEY = Key.create<Boolean>("PositionDiffArrowInitializer.POSITION_CHANGED_KEY")
    private const val DIFF_ICON_RIGHT_MARGIN = 4
    private val EMPTY_DIFF_ICON = IconManager.getInstance().createEmptyIcon(CompletionMlRankingIcons.ProposalUp)
  }

  override fun projectOpened(project: Project) {
    LookupManager.getInstance(project).addPropertyChangeListener(object : LookupTracker() {
      private fun shouldShowDiff(lookupStorage: LookupStorage): Boolean {
        val mlRankingSettings = CompletionMLRankingSettings.getInstance()
        return lookupStorage.model != null && mlRankingSettings.isShowDiffEnabled
      }

      override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
        if (!shouldShowDiff(storage)) return

        lookup.addPresentationCustomizer(object : LookupCellRenderer.ItemPresentationCustomizer {
          override fun customizePresentation(item: LookupElement,
                                             presentation: LookupElementPresentation): LookupElementPresentation {
            val positionChanged = lookup.getUserData(POSITION_CHANGED_KEY)
            if (positionChanged == null || !positionChanged) return presentation
            val newPresentation = LookupElementPresentation()
            newPresentation.copyFrom(presentation)
            val diff = item.getUserData(POSITION_DIFF_KEY)?.get()
            val diffIcon = when {
              diff == null || diff == 0 -> EMPTY_DIFF_ICON
              diff < 0 -> CompletionMlRankingIcons.ProposalUp
              else -> CompletionMlRankingIcons.ProposalDown
            }
            val diffIconWithMargin = iconWithRightMargin(diffIcon)
            newPresentation.icon = ArrowDecoratedIcon(diffIconWithMargin, newPresentation.icon)
            return newPresentation
          }
        })
      }
    }, project)
  }

  private class ArrowDecoratedIcon(private val arrowIcon: Icon, private val baseIcon: Icon?) :
    com.intellij.ui.RowIcon(2, RowIcon.Alignment.CENTER), LookupCellRenderer.ItemPresentationCustomizer.IconDecorator {

    init {
      super.setIcon(arrowIcon, 0)
      super.setIcon(baseIcon, 1)
    }

    override fun getDelegate(): Icon? = baseIcon
    override fun withDelegate(icon: Icon): LookupCellRenderer.ItemPresentationCustomizer.IconDecorator = ArrowDecoratedIcon(arrowIcon, icon)
  }

  private fun iconWithRightMargin(icon: Icon, margin: Int = DIFF_ICON_RIGHT_MARGIN): IconUtil.IconSizeWrapper {
    return object : IconUtil.IconSizeWrapper(icon, icon.iconWidth + margin, icon.iconHeight) {
      override fun paintIcon(icon: Icon?, c: Component?, g: Graphics?, x: Int, y: Int) {
        if (icon == null) return
        icon.paintIcon(c, g, x, y)
      }
    }
  }
}