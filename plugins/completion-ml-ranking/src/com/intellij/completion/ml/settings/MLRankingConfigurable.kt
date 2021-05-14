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
import com.intellij.util.IconUtil
import icons.CompletionMlRankingIcons
import java.awt.Rectangle
import javax.swing.Icon

class MLRankingConfigurable(private val availableProviders: List<RankingModelProvider>) :
  BoundConfigurable(MLCompletionBundle.message("ml.completion.settings.group")) {
  private val settings = CompletionMLRankingSettings.getInstance()

  companion object {
    val UP_DOWN_ICON = createUpDownIcon()
    val RELEVANT_ICON = cropIcon(CompletionMlRankingIcons.RelevantProposal)

    private fun createUpDownIcon(): Icon {
      val icon = IconManager.getInstance().createRowIcon(2, RowIcon.Alignment.CENTER)
      icon.setIcon(cropIcon(CompletionMlRankingIcons.ProposalUp), 0)
      icon.setIcon(cropIcon(CompletionMlRankingIcons.ProposalDown), 1)
      return icon
    }

    private fun cropIcon(icon: Icon): Icon = IconUtil.cropIcon(icon, Rectangle(4, 0, 8, 16))
  }

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
              JBLabel(UP_DOWN_ICON)()
            }
          }
        }
        row {
          cell {
            enableRankingCheckbox?.let { enableRanking ->
              checkBox(MLCompletionBundle.message("ml.completion.decorate.relevant"),
                       { settings.isDecorateRelevantEnabled },
                       { settings.isDecorateRelevantEnabled = it }).enableIf(enableRanking.selected)
              JBLabel(RELEVANT_ICON)()
            }
          }
        }
      }
    }
  }
}
