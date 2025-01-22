package org.jetbrains.idea.devkit.fleet.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.devkit.DevKitIcons
import org.jetbrains.idea.devkit.fleet.DevKitFleetBundle
import org.jetbrains.idea.devkit.fleet.inspections.settingRegistrationOrNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import javax.swing.Icon

class NavigateToRegistrationLineMarkerProvider : LineMarkerProvider {
  private val icon: Icon = DevKitIcons.Gutter.Plugin
  private val CONVERTER: (KtCallExpression) -> Collection<PsiElement?> = { call -> ContainerUtil.createMaybeSingletonList(call) }
  private val RELATED_ITEM_PROVIDER: (KtCallExpression) -> Collection<GotoRelatedItem?> = { call -> GotoRelatedItem.createItems(setOf(call), "DevKit") }

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    elements.forEach { element ->
      if (element !is KtCallExpression) return@forEach
      analyze(element) {
        val registration = element.settingRegistrationOrNull() ?: return@forEach
        result.add(
          NavigationGutterIconBuilder
            .create(icon, CONVERTER, RELATED_ITEM_PROVIDER)
            .setTargets(registration)
            .setTooltipText(DevKitFleetBundle.message("tooltip.registration"))
            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
            .createLineMarkerInfo(element)
        )
      }
    }
  }
}
