// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ClientProperty
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBFont
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

class PromoFeaturePage(
  val productIcon: Icon,
  val suggestedIde: SuggestedIde,
  @NlsContexts.Label val descriptionHtml: String,
  val features: List<PromoFeatureListItem>,
  @NlsContexts.Label val trialLabel: String,
  val pluginId: String?
)

class PromoFeatureListItem(
  val icon: Icon,
  @NlsContexts.Label val title: String
)

object PromoPages {
  fun build(page: PromoFeaturePage): DialogPanel {
    return build(page, FUSEventSource.SETTINGS)
  }

  fun build(page: PromoFeaturePage, source: FUSEventSource): DialogPanel {
    val panel = panel {
      row {
        icon(page.productIcon)

        label(FeaturePromoBundle.message("upgrade.to", page.suggestedIde.name))
          .applyToComponent {
            font = JBFont.label().biggerOn(3.0f).asBold()
          }
      }.layout(RowLayout.PARENT_GRID)

      row {
        cell()

        text(page.descriptionHtml) {
          source.learnMoreAndLog(null, it.url.toExternalForm(), page.pluginId?.let(PluginId::getId))
        }
      }.layout(RowLayout.PARENT_GRID)

      row {
        cell()

        panel {
          for (feature in page.features) {
            row {
              icon(feature.icon).gap(RightGap.SMALL)
              label(feature.title)
            }
          }
        }

        bottomGap(BottomGap.MEDIUM)
        layout(RowLayout.PARENT_GRID)
      }

      row {
        cell()

        (button(FeaturePromoBundle.message("get.prefix", page.suggestedIde.name)) {
          source.openDownloadPageAndLog(null, page.suggestedIde.downloadUrl, page.pluginId?.let(PluginId::getId))
        }).applyToComponent {
          this.icon = AllIcons.Ide.External_link_arrow
          this.horizontalTextPosition = SwingConstants.LEFT
          ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }

        comment(page.trialLabel)
      }.layout(RowLayout.PARENT_GRID)
    }

    return panel
  }
}