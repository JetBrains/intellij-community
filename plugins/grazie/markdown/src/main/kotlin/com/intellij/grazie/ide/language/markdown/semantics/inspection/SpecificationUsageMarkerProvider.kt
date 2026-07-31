package com.intellij.grazie.ide.language.markdown.semantics.inspection

import com.intellij.application.options.editor.GutterIconsConfigurable
import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.ide.language.markdown.semantics.analyzer.SpecificationAnalyzer
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isAnalysisEnabled
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isSpecificationLikeFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import java.time.format.DateTimeFormatter
import java.util.function.Supplier
import javax.swing.Icon

private val costTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal class SpecificationUsageMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName(): @GutterName String = GrazieBundle.message("specification.gutter.progress.text")
  override fun getIcon(): Icon = GrazieIcons.Stroke.GrazieCloudProcessing

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    val file = element as? MarkdownFile ?: return null
    if (!isAnalysisEnabled() || !isSpecificationLikeFile(file)) return null
    return UsageMarkerInfo(file, GrazieBundle.message("specification.gutter.progress.tooltip"))
  }

  private class UsageMarkerInfo(file: MarkdownFile, @NlsSafe private val tooltip: String) :
    LineMarkerInfo<MarkdownFile>(
      file, file.textRange,
      GrazieIcons.Stroke.GrazieCloudProcessing, { tooltip }, null,
      GutterIconRenderer.Alignment.LEFT, { tooltip }
    ) {
    override fun getLineMarkerTooltip(): @NlsContexts.Tooltip String? {
      val file = element as? PsiFile ?: return null
      val costs = SpecificationAnalyzer.getCosts(file) ?: return tooltip
      return GrazieBundle.message(
        "specification.gutter.progress.tooltip.cost",
        costTimeFormatter.format(costs.since),
        costs.credits,
      )
    }

    override fun createGutterRenderer(): GutterIconRenderer {
      return object : LineMarkerGutterIconRenderer<MarkdownFile>(this) {
        override fun isNavigateAction() = true
        override fun getPopupMenuActions() =
          DefaultActionGroup(
            ShowSettingsAction(
              GrazieBundle.messagePointer("specification.gutter.progress.disable")
            )
          )
      }
    }
  }

  private class ShowSettingsAction : DumbAwareAction {
    constructor(dynamicText: Supplier<String>) : super(dynamicText)
    override fun actionPerformed(e: AnActionEvent) =
      ShowSettingsUtil.getInstance().showSettingsDialog(e.project, GutterIconsConfigurable::class.java)
  }
}
