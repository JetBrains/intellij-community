// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

internal class KotlinDeleteCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = getCommandContext(context.offset, context.psiFile) ?: return emptyList()
    var psiElement = PsiTreeUtil.getParentOfType(element, KtExpression::class.java, KtNamedDeclaration::class.java) ?: return emptyList()
    val hasTheSameOffset = psiElement.textRange.endOffset == context.offset
    if (!hasTheSameOffset) return emptyList()
    psiElement = getTopWithTheSameOffset(psiElement, context.offset)
    val highlightInfo = HighlightInfoLookup(psiElement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
      val command = createCommand(highlightInfo, psiElement) ?: return emptyList<CompletionCommand>()
      return listOf(command)
  }

    private fun createCommand(highlightInfo: HighlightInfoLookup, psiElement: PsiElement): CompletionCommand? {
        val copy = psiElement.containingFile.copy() as PsiFile
        val previewBefore = copy.text
        val elementToDelete = PsiTreeUtil.findSameElementInCopy(psiElement, copy)
        if (elementToDelete is KtClassOrObject) {
            val file = elementToDelete.containingFile as? KtFile ?: return null
            if (elementToDelete.isTopLevel() && file.declarations.size <= 1) {
                return null
            }
        }
        elementToDelete.delete()
        val previewAfter = copy.text
        val preview: IntentionPreviewInfo = IntentionPreviewInfo.CustomDiff(
            KotlinFileType.INSTANCE,
            null,
            previewBefore,
            previewAfter,
            true
        )
        return KotlinDeleteCompletionCommand(highlightInfo, preview)
    }
}

private fun getTopWithTheSameOffset(psiElement: KtExpression, offset: Int): KtExpression {
    var psiElement1 = psiElement
    var curElement = psiElement1
    while (curElement.textRange.endOffset == offset) {
        psiElement1 = curElement
        curElement = PsiTreeUtil.getParentOfType(curElement, KtExpression::class.java, KtNamedDeclaration::class.java) ?: break
    }
    return psiElement1
}

private class KotlinDeleteCompletionCommand(
  override val highlightInfo: HighlightInfoLookup?,
  private val preview: IntentionPreviewInfo,
) : CompletionCommand(), CompletionCommandWithPreview, DumbAware {
  override val name: String
    get() = "Delete element"
  override val i18nName: @Nls String
    get() = ActionsBundle.message("action.EditorDelete.text")
  override val icon: Icon?
    get() = null
  override val priority: Int
        get() = -100

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getCommandContext(offset, psiFile) ?: return
    var psiElement = PsiTreeUtil.getParentOfType(element, KtExpression::class.java, KtNamedDeclaration::class.java) ?: return
    psiElement = getTopWithTheSameOffset(psiElement, offset)
    WriteCommandAction.runWriteCommandAction(psiFile.project, null, null, {
      val parent: SmartPsiElementPointer<PsiElement?> = SmartPointerManager.createPointer(psiElement.parent ?: psiFile)
      psiElement.delete()
      PsiDocumentManager.getInstance(psiFile.project).commitDocument(psiFile.fileDocument)
      parent.element?.let {
        ReformatCodeProcessor(psiFile, arrayOf(it.textRange)).run()
      }
    }, psiFile)
  }

  override fun getPreview(): IntentionPreviewInfo {
    return preview
  }
}