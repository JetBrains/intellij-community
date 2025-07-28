// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.settings

import com.intellij.completion.ml.CompletionMlRankingIcons
import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.icons.RowIcon
import com.intellij.util.IconUtil
import java.awt.Rectangle
import javax.swing.Icon

internal val UP_DOWN_ICON = createUpDownIcon()
internal val RELEVANT_ICON = cropIcon(CompletionMlRankingIcons.RelevantProposal)

private fun createUpDownIcon(): Icon {
  val icon = IconManager.getInstance().createRowIcon(2, RowIcon.Alignment.CENTER)
  icon.setIcon(cropIcon(CompletionMlRankingIcons.ProposalUp), 0)
  icon.setIcon(cropIcon(CompletionMlRankingIcons.ProposalDown), 1)
  return icon
}

private fun cropIcon(icon: Icon): Icon = IconUtil.cropIcon(icon, Rectangle(4, 0, 8, 16))

internal class MLRankingConfigurable(private val availableProviders: List<RankingModelProvider>) :
  UiDslUnnamedConfigurable.Simple(), Configurable {

  private val settings = CompletionMLRankingSettings.getInstance()

  override fun getDisplayName(): String = MLCompletionBundle.message("ml.completion.settings.group")

  override fun Panel.createContent() {
    val providers = availableProviders
      .distinctBy { it.displayNameInSettings }
      .sortedBy { it.displayNameInSettings }

    lateinit var enableRankingCheckbox: Cell<JBCheckBox>
    row {
      enableRankingCheckbox = checkBox(MLCompletionBundle.message("ml.completion.enable"))
        .bindSelected(settings::isRankingEnabled, settings::setRankingEnabled)
        .gap(RightGap.SMALL)
      contextHelp(MLCompletionBundle.message("ml.completion.enable.help"))
    }
    indent {
      for (ranker in providers) {
        row {
          checkBox(ranker.displayNameInSettings)
            .bindSelected({ settings.isLanguageEnabled(ranker.id) }, { settings.setLanguageEnabled(ranker.id, it) })
            .enabledIf(enableRankingCheckbox.selected)
        }.apply { if (ranker === providers.last()) bottomGap(BottomGap.SMALL) }
      }
    }
    row {
      checkBox(MLCompletionBundle.message("ml.completion.show.diff"))
        .bindSelected(settings::isShowDiffEnabled, settings::setShowDiffEnabled)
        .enabledIf(enableRankingCheckbox.selected)
        .gap(RightGap.SMALL)
      icon(UP_DOWN_ICON)
    }
    row {
      checkBox(MLCompletionBundle.message("ml.completion.decorate.relevant"))
        .bindSelected(settings::isDecorateRelevantEnabled, settings::setDecorateRelevantEnabled)
        .enabledIf(enableRankingCheckbox.selected)
        .gap(RightGap.SMALL)
      icon(RELEVANT_ICON)
    }
  }
}
