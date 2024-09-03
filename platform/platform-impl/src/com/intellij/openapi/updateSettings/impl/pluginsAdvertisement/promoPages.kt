// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ClientProperty
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.SwingConstants

@ApiStatus.Internal
open class PromoFeaturePage(
  val productIcon: Icon,
  val suggestedIde: SuggestedIde,
  @NlsContexts.Label val descriptionHtml: String,
  val features: List<PromoFeatureListItem>,
  @NlsContexts.Label val trialLabel: String,
  val pluginId: String?,
  @NlsContexts.Label
  val disablePromotionText: String? = null,
  @NlsContexts.HintText
  val disablePromotionComment: String? = null
) {
  open fun getButtonOpenPromotionText(): @Nls String = FeaturePromoBundle.message("get.prefix.with.placeholder", suggestedIde.name)

  @Nls
  open fun getTitle(): String = FeaturePromoBundle.message("upgrade.to", suggestedIde.name)
}

@ApiStatus.Internal
class PromoFeatureListItem(
  val icon: Icon,
  @NlsContexts.Label val title: String
)

@ApiStatus.Internal
object PromoPages {
  fun build(
    page: PromoFeaturePage,
    openLearnMore: (url: String) -> Unit,
    openDownloadLink: (DialogWrapper?) -> Unit,
    disablePromotionStatus: Boolean? = null,
    disablePromotionHandler: ((Boolean) -> Unit)? = null,
  ): DialogPanel {
    val panel = panel {
      row {
        icon(page.productIcon)

        label(page.getTitle())
          .applyToComponent {
            font = JBFont.label().biggerOn(3.0f).asBold()
          }
      }.layout(RowLayout.PARENT_GRID)

      row {
        cell()
        text(page.descriptionHtml) {
          openLearnMore(it.url.toExternalForm())
        }
      }.layout(RowLayout.PARENT_GRID)

      row {
        cell()

        panel {
          for (feature in page.features) {
            row {
              icon(feature.icon).gap(RightGap.SMALL).align(AlignY.TOP)
              text(feature.title)
            }
          }
        }

        bottomGap(BottomGap.MEDIUM)
        layout(RowLayout.PARENT_GRID)
      }

      row {
        cell()
        (button(page.getButtonOpenPromotionText()) { event ->
          val source = event.source as? JButton
          val dialog = source?.parent?.let { DialogWrapper.findInstance(it) }
          openDownloadLink(dialog)
        }).applyToComponent {
          this.icon = AllIcons.Ide.ExternalLinkArrowWhite
          this.horizontalTextPosition = SwingConstants.LEFT
          ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }

        comment(page.trialLabel)
      }.layout(RowLayout.PARENT_GRID)

      if (page.disablePromotionText != null) {
        group {
          row {
            cell()
            checkBox(page.disablePromotionText).comment(page.disablePromotionComment ?: "").onChanged {
              disablePromotionHandler?.invoke(!it.isSelected)
            }.applyToComponent {
              isSelected = disablePromotionStatus ?: false
            }
          }.layout(RowLayout.PARENT_GRID)
        }
      }
    }
    return panel
  }

  fun buildWithTryUltimate(
    page: PromoFeaturePage,
    openLearnMore: ((url: String) -> Unit)? = null,
    openDownloadLink: (() -> Unit)? = null,
    source: FUSEventSource = FUSEventSource.SETTINGS,
  ): DialogPanel {
    source.logIdeSuggested(null, page.suggestedIde.productCode, page.pluginId?.let { PluginId.getId(it) })

    val pluginId = page.pluginId?.let(PluginId::getId)
    val project = ProjectManager.getInstance().openProjects.firstOrNull()
    
    return build(
      page = page,
      openLearnMore = { openLearnMore?.invoke(it) ?: source.learnMoreAndLog(project, it, pluginId) },
      openDownloadLink = { dialog ->
        tryUltimate(pluginId, page.suggestedIde, project, source, openDownloadLink)
        dialog?.close(DialogWrapper.CLOSE_EXIT_CODE)
      }
    )
  }
}