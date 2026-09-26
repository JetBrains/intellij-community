// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.android.kmpPluginPromotion

import com.android.tools.idea.npw.template.PluginPromotionTemplate
import com.android.tools.idea.npw.template.WizardPluginPromotionTemplateProvider
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Thumb
import com.intellij.compose.ide.plugin.shared.android.ComposeIdeAndroidBundle

internal class KmpPluginPlaceholderProvider : WizardPluginPromotionTemplateProvider() {
  override fun getTemplates(): List<PluginPromotionTemplate> =
    listOf(
      promotionTemplate(FormFactor.Mobile),
      promotionTemplate(FormFactor.Generic),
    )

  private fun promotionTemplate(section: FormFactor): PluginPromotionTemplate =
    object : PluginPromotionTemplate {
      override val name: String
        get() = ComposeIdeAndroidBundle.message("kmp.wizard.promotion.title")

      override val pluginId: String
        get() = KMP_PLUGIN_ID

      override fun thumb() =
        Thumb { this@KmpPluginPlaceholderProvider.javaClass.getResource("/META-INF/kmp_required_logo.png") }

      override val formFactor: FormFactor
        get() = section
    }


  companion object {
    private const val KMP_PLUGIN_ID = "com.jetbrains.kmm"
  }
}