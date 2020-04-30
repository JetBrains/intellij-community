// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchSort
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke

object GHPRSearchPanel {

  fun create(project: Project, model: SingleValueModel<String>): JComponent {
    val searchField = object : TextFieldWithCompletion(project, SearchCompletionProvider(project), "", true, true, false, false) {
      override fun setupBorder(editor: EditorEx) {
        editor.setBorder(JBUI.Borders.empty(6, 5))
      }

      override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
        if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
          model.value = text
          return true
        }
        return super.processKeyBinding(ks, e, condition, pressed)
      }
    }.apply {
      addSettingsProvider {
        it.putUserData(AutoPopupController.NO_ADS, true)
        UIUtil.setNotOpaqueRecursively(it.component)
      }
    }

    val icon = JLabel(AllIcons.Actions.Find).apply {
      border = JBUI.Borders.emptyLeft(5)
    }

    model.addAndInvokeValueChangedListener {
      searchField.text = model.value
    }

    return JBUI.Panels.simplePanel(searchField).addToLeft(icon).withBackground(UIUtil.getListBackground())
  }

  private class SearchCompletionProvider(project: Project) : TextFieldCompletionProviderDumbAware(true) {
    private val addColonInsertHandler = object : InsertHandler<LookupElement> {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (context.completionChar == ':') return
        val editor = context.editor
        if (!isAtColon(context)) {
          EditorModificationUtil.insertStringAtCaret(editor, ":")
          context.commitDocument()
        }
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
      }

      private fun isAtColon(context: InsertionContext): Boolean {
        val startOffset = context.startOffset
        val document = context.document
        return document.textLength > startOffset && document.charsSequence[startOffset] == ':'
      }
    }

    override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
      val qualifierName = getCurrentQualifierName(text, offset)
      if (qualifierName == null) {
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.state)
                            .withTailText(":")
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.author)
                            .withTailText(":")
                            .withTypeText("username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.assignee)
                            .withTailText(":")
                            .withTypeText("username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.after)
                            .withTailText(":")
                            .withTypeText("YYYY-MM-DD", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.before)
                            .withTailText(":")
                            .withTypeText("YYYY-MM-DD", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GHPRSearchQuery.QualifierName.sortBy)
                            .withTailText(":")
                            .withInsertHandler(addColonInsertHandler))
      }
      else when {
        qualifierName.equals(GHPRSearchQuery.QualifierName.state.name, true) -> {
          for (state in GithubIssueState.values()) {
            result.addElement(LookupElementBuilder.create(state.name))
          }
        }
        qualifierName.equals(GHPRSearchQuery.QualifierName.sortBy.name, true) -> {
          for (sort in GithubIssueSearchSort.values()) {
            result.addElement(LookupElementBuilder.create(sort.name))
          }
        }
      }
    }

    /**
     * Prefix is the char sequence from last space or first colon after space/line start to caret
     */
    override fun getPrefix(currentTextPrefix: String): String {
      val spaceIdx = currentTextPrefix.lastIndexOf(' ')
      val colonIdx = currentTextPrefix.indexOf(':', Math.max(spaceIdx, 0))
      return currentTextPrefix.substring(Math.max(spaceIdx, colonIdx) + 1)
    }

    /**
     * Current qualifier name is the nearest char sequence in between space and colon or before first colon
     * "qname:test" -> "qname"
     * "qname:test:test" -> "qname"
     * " qname:test:test" -> "qname"
     * " qname:test:test " -> null
     */
    private fun getCurrentQualifierName(text: String, offset: Int): String? {
      val spaceIdx = text.lastIndexOf(' ', offset - 1)
      val colonIdx = text.indexOf(':', Math.max(spaceIdx, 0))
      if (colonIdx < 0 || spaceIdx > colonIdx) return null
      return text.substring(spaceIdx + 1, colonIdx)
    }
  }
}