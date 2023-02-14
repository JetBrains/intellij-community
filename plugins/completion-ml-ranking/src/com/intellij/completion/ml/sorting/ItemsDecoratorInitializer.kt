// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.application.options.CodeCompletionConfigurable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.CompletionMlRankingIcons
import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.settings.MLCompletionSettingsCollector
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconManager
import com.intellij.ui.IconReplacer
import com.intellij.ui.icons.RowIcon
import com.intellij.util.IconUtil
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

class ItemsDecoratorInitializer : LookupTracker() {
  companion object {
    private const val SHOW_STAR_NOTIFICATION_REGISTRY = "completion.ml.show.star.notification"
    private const val STAR_OPINION_NOTIFICATION_SHOWN_KEY = "completion.ml.star.opinion.notification.shown"
    private const val STAR_NOTIFICATION_AFTER_SESSIONS = 50
    private val sessionsWithStarCounter = AtomicInteger()

    private val POSITION_DIFF_KEY = Key.create<AtomicInteger>("ItemsDecoratorInitializer.POSITION_DIFF_KEY")
    private val POSITION_CHANGED_KEY = Key.create<Boolean>("ItemsDecoratorInitializer.POSITION_CHANGED_KEY")
    private val HAS_RELEVANT_KEY = Key.create<Boolean>("ItemsDecoratorInitializer.HAS_RELEVANT_KEY")
    private val IS_RELEVANT_KEY = Key.create<Boolean>("ItemsDecoratorInitializer.IS_RELEVANT_KEY")

    private val EMPTY_ICON = prepareIcon(IconManager.getInstance().createEmptyIcon(
      CompletionMlRankingIcons.RelevantProposal))
    private val RELEVANT_ICON = prepareIcon(CompletionMlRankingIcons.RelevantProposal)
    private val DOWN_ICON = prepareIcon(CompletionMlRankingIcons.ProposalDown)
    private val UP_ICON = prepareIcon(CompletionMlRankingIcons.ProposalUp)

    fun markAsReordered(lookup: LookupImpl, value: Boolean) {
      val changed = lookup.getUserData(POSITION_CHANGED_KEY)
      if (changed == null) {
        lookup.putUserData(POSITION_CHANGED_KEY, value)
      }
    }

    fun itemPositionChanged(element: LookupElement, diffValue: Int) {
      val diff = element.getUserData(POSITION_DIFF_KEY) ?: AtomicInteger()
        .apply { element.putUserData(POSITION_DIFF_KEY, this) }

      diff.set(diffValue)
    }

    fun markAsRelevant(lookup: LookupImpl, element: LookupElement) {
      lookup.putUserData(HAS_RELEVANT_KEY, true)
      element.putUserData(IS_RELEVANT_KEY, true)
      showStarNotificationIfNeeded()
    }

    private fun shouldShowStarNotification(): Boolean = Registry.`is`(SHOW_STAR_NOTIFICATION_REGISTRY, true) &&
                                                        ApplicationInfoEx.getInstanceEx().isEAP

    private fun showStarNotificationIfNeeded() {
      val properties = PropertiesComponent.getInstance()
      if (shouldShowStarNotification() && !properties.getBoolean(STAR_OPINION_NOTIFICATION_SHOWN_KEY)) {
        val sessionsCount = sessionsWithStarCounter.incrementAndGet()
        if (sessionsCount == STAR_NOTIFICATION_AFTER_SESSIONS) {
          properties.setValue(STAR_OPINION_NOTIFICATION_SHOWN_KEY, true)
          StarOpinionNotification().notify(null)
        }
      }
    }

    private fun prepareIcon(icon: Icon) = IconUtil.cropIcon(icon, Rectangle(4,0, 12,16))
  }

  override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
    if (shouldShowDiff(storage) || shouldShowRelevant(storage)) {
      lookup.addPresentationCustomizer(object : LookupCellRenderer.ItemPresentationCustomizer {
        override fun customizePresentation(item: LookupElement,
                                           presentation: LookupElementPresentation): LookupElementPresentation {
          val shouldShowRelevant = lookup.getUserData(HAS_RELEVANT_KEY) ?: false
          val shouldShowDiff = lookup.getUserData(POSITION_CHANGED_KEY) ?: false
          if (!shouldShowRelevant && !shouldShowDiff) return presentation

          val isRelevant = item.getUserData(IS_RELEVANT_KEY) ?: false
          val diff = item.getUserData(POSITION_DIFF_KEY)?.get() ?: 0
          val newPresentation = LookupElementPresentation()
          newPresentation.copyFrom(presentation)
          val decorationIcon = when {
            shouldShowRelevant && isRelevant -> RELEVANT_ICON
            shouldShowDiff && diff < 0 -> UP_ICON
            shouldShowDiff && diff > 0 -> DOWN_ICON
            else -> EMPTY_ICON
          }
          newPresentation.icon = LeftDecoratedIcon(decorationIcon, newPresentation.icon)
          return newPresentation
        }
      })
    }
  }

  private fun shouldShowDiff(lookupStorage: LookupStorage): Boolean {
    val mlRankingSettings = CompletionMLRankingSettings.getInstance()
    return lookupStorage.model != null && mlRankingSettings.isShowDiffEnabled
  }

  private fun shouldShowRelevant(lookupStorage: LookupStorage): Boolean {
    val mlRankingSettings = CompletionMLRankingSettings.getInstance()
    val model = lookupStorage.model ?: return false
    return mlRankingSettings.isDecorateRelevantEnabled && model.decoratingPolicy() != DecoratingItemsPolicy.DISABLED
  }

  class LeftDecoratedIcon(private val leftIcon: Icon, private val baseIcon: Icon?) :
    com.intellij.ui.RowIcon(2, RowIcon.Alignment.CENTER), LookupCellRenderer.IconDecorator {

    init {
      super.setIcon(leftIcon, 0)
      super.setIcon(baseIcon, 1)
    }

    override fun getDelegate(): Icon? = baseIcon
    override fun withDelegate(icon: Icon?): LookupCellRenderer.IconDecorator = LeftDecoratedIcon(leftIcon, icon)

    override fun replaceBy(replacer: IconReplacer): LeftDecoratedIcon {
      return LeftDecoratedIcon(replacer.replaceIcon(leftIcon), replacer.replaceIcon(baseIcon))
    }
  }

  private class StarOpinionNotification : Notification(
    MLCompletionBundle.message("ml.completion.notification.groupId"),
    MLCompletionBundle.message("ml.completion.notification.title"),
    MLCompletionBundle.message("ml.completion.notification.decorating.opinion.content"),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.like")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(e.project, MLCompletionSettingsCollector.DecorationOpinion.LIKE)
          notification.expire()
        }
      })
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.dislike")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(e.project, MLCompletionSettingsCollector.DecorationOpinion.DISLIKE)
          CompletionMLRankingSettings.getInstance().isDecorateRelevantEnabled = false
          notification.expire()
          StarDisabledNotification().notify(null)
        }
      })
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.opinion.neutral")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(e.project, MLCompletionSettingsCollector.DecorationOpinion.NEUTRAL)
          notification.expire()
        }
      })
    }
  }

  private class StarDisabledNotification : Notification(
    MLCompletionBundle.message("ml.completion.notification.groupId"),
    MLCompletionBundle.message("ml.completion.notification.title"),
    MLCompletionBundle.message("ml.completion.notification.decorating.disabled.content", ShowSettingsUtil.getSettingsMenuName()),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.configure")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, CodeCompletionConfigurable::class.java)
          notification.expire()
        }
      })
    }
  }
}