package org.jetbrains.idea.devkit.fleet.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.devkit.DevKitIcons
import org.jetbrains.idea.devkit.fleet.DevKitFleetBundle
import org.jetbrains.idea.devkit.fleet.inspections.analyzeCallExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import javax.swing.Icon

class NavigateToDocumentationLineMarkerProvider : LineMarkerProvider {
  private val icon: Icon = DevKitIcons.Gutter.DescriptionFile
  private val CONVERTER: (PsiFile) -> Collection<PsiElement?> = { psiFile -> ContainerUtil.createMaybeSingletonList(psiFile) }
  private val RELATED_ITEM_PROVIDER: (PsiFile) -> Collection<GotoRelatedItem?> = { psiFile -> GotoRelatedItem.createItems(setOf(psiFile), "DevKit") }

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    elements.forEach { element ->
      if (element !is KtCallExpression) return@forEach
      val info = analyzeCallExpression(element)
      info?.file?.findPsiFile(element.project)?.let { psiFile ->
        result.add(
          NavigationGutterIconBuilder
            .create(icon, CONVERTER, RELATED_ITEM_PROVIDER)
            .setTargets(psiFile)
            .setTooltipText(DevKitFleetBundle.message("tooltip.documentation"))
            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
            .createLineMarkerInfo(element)
        )
      }
    }
  }
}
