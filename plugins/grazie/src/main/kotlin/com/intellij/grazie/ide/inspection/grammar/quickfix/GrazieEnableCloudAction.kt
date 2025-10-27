package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.utils.isPromotionAllowed
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

private val logger = logger<GrazieEnableCloudAction>()

class GrazieEnableCloudAction : IntentionAndQuickFixAction(), Iconable, CustomizableIntentionAction {

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean =
    isPromotionAllowed && !GrazieCloudConnector.hasAdditionalConnectors() && GrazieConfig.get().explicitlyChosenProcessing == null

  override fun getName(): @IntentionName String = GrazieBundle.message("grazie.cloud.enable.action.text")

  override fun getFamilyName(): @IntentionFamilyName String = name

  override fun isShowSubmenu(): Boolean = false

  override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
    if (!GrazieCloudConnector.askUserConsentForCloud()) return
    logger.debug { "Connect to Grazie Cloud button started from action" }
    if (!GrazieCloudConnector.isAuthorized() && !GrazieCloudConnector.connect(project)) return
    GrazieConfig.update { it.copy(explicitlyChosenProcessing = Processing.Cloud) }
  }

  override fun startInWriteAction(): Boolean = false

  override fun getIcon(flags: Int): Icon = GrazieIcons.Stroke.Grazie


}