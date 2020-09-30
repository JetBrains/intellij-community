// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.settings

import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.icons.RowIcon
import com.intellij.ui.layout.*
import icons.CompletionMlRankingIcons
import javax.swing.Icon

class MLRankingConfigurable(private val availableProviders: List<RankingModelProvider>) :
  BoundConfigurable(MLCompletionBundle.message("ml.completion.settings.group")) {
  private val settings = CompletionMLRankingSettings.getInstance()

  override fun createPanel(): DialogPanel {
    val providers = availableProviders.distinctBy { it.displayNameInSettings }.sortedBy { it.displayNameInSettings }
    return panel {
      var enableRankingCheckbox: CellBuilder<JBCheckBox>? = null
      titledRow(displayName) {
        row {
          cell {
            enableRankingCheckbox = checkBox(MLCompletionBundle.message("ml.completion.enable"), settings::isRankingEnabled,
                                         { settings.isRankingEnabled = it })
            ContextHelpLabel.create(MLCompletionBundle.message("ml.completion.enable.help"))()
          }
          for (ranker in providers) {
            row {
              enableRankingCheckbox?.let { enableRanking ->
                checkBox(ranker.displayNameInSettings, { settings.isLanguageEnabled(ranker.id) },
                         { settings.setLanguageEnabled(ranker.id, it) })
                  .enableIf(enableRanking.selected)
              }
            }.apply { if (ranker === providers.last()) largeGapAfter() }
          }
        }
        row {
          cell {
            enableRankingCheckbox?.let { enableRanking ->
              checkBox(MLCompletionBundle.message("ml.completion.show.diff"),
                       { settings.isShowDiffEnabled },
                       { settings.isShowDiffEnabled = it }).enableIf(enableRanking.selected)
              JBLabel(createUpDownIcon())()
            }
          }
        }
      }
    }
  }

  private fun createUpDownIcon(): Icon {
    val icon = IconManager.getInstance().createRowIcon(2, RowIcon.Alignment.CENTER)
    icon.setIcon(CompletionMlRankingIcons.ProposalUp, 0)
    icon.setIcon(CompletionMlRankingIcons.ProposalDown, 1)
    return icon
  }
}
