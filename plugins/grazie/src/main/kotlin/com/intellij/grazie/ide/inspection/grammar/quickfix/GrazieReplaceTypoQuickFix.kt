// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import icons.SpellcheckerIcons
import javax.swing.Icon
import kotlin.math.min

class GrazieReplaceTypoQuickFix(private val typo: Typo) : LocalQuickFix, Iconable, PriorityAction {
  override fun getFamilyName() = msg("grazie.grammar.quickfix.replace.typo.family")

  override fun getName() = msg("grazie.grammar.quickfix.replace.typo.text", typo.info.shortMessage)

  override fun getIcon(flags: Int): Icon = SpellcheckerIcons.Spellcheck

  override fun getPriority() = PriorityAction.Priority.HIGH

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
      var editor: Editor = CommonDataKeys.EDITOR.getData(context) ?: return@onSuccess
      val element = typo.location.element!!
      if (InjectedLanguageManager.getInstance(project).getInjectionHost(element) != null && editor !is EditorWindow) {
        editor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, element.containingFile)
      }

      val selectionRange = descriptor.textRangeInElement.shiftRight(element.textOffset)
      if (editor.document.getText(selectionRange) == typo.location.errorText) {
        editor.selectionModel.setSelection(selectionRange.startOffset, min(selectionRange.endOffset, editor.document.textLength))
        val items = typo.fixes.map { LookupElementBuilder.create(it) }
        LookupManager.getInstance(project).showLookup(editor, *items.toTypedArray())?.registerFUCollector(typo)
      }
    }
  }

  private fun LookupEx.registerFUCollector(typo: Typo) {
    addLookupListener(object : LookupListener {
      override fun lookupCanceled(event: LookupEvent) {
        GrazieFUSCounter.quickfixApplied(typo.info.rule.id, cancelled = true)
      }

      override fun beforeItemSelected(event: LookupEvent): Boolean {
        runWriteAction {
          val length = editor.document.textLength
          val shift = typo.location.element!!.textOffset
          for ((begin, end) in typo.location.textRanges.reversed().map { it.start + shift to it.endInclusive + shift }) {
            if (begin < length) {
              editor.document.deleteString(begin, min(end, length))
            }
          }
        }

        editor.selectionModel.removeSelection()
        return true
      }

      override fun itemSelected(event: LookupEvent) {
        GrazieFUSCounter.quickfixApplied(typo.info.rule.id, cancelled = false)
      }
    })
  }
}
