// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

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
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.search.GithubIssueSearchSort
import java.awt.Graphics
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.KeyStroke
import javax.swing.border.EmptyBorder

class GithubPullRequestSearchComponent(project: Project,
                                       private val autoPopupController: AutoPopupController,
                                       private val model: GithubPullRequestSearchModel) : Wrapper() {

  private val searchField = object : TextFieldWithCompletion(project, SearchCompletionProvider(), "", true, true, false, false) {
    private val ICON = AllIcons.Actions.Find
    private val ICON_RIGHT_MARGIN = JBUI.scale(4)

    override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
      if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
        updateQuery()
        return true
      }
      return super.processKeyBinding(ks, e, condition, pressed)
    }

    override fun createEditor(): EditorEx {
      return super.createEditor().apply {
        putUserData(AutoPopupController.NO_ADS, true)
      }
    }

    override fun setupBorder(editor: EditorEx) {
      super.setupBorder(editor)
      editor.setBorder(BorderFactory.createCompoundBorder(editor.scrollPane.border,
                                                          EmptyBorder(0, ICON.iconWidth + ICON_RIGHT_MARGIN, 0, 0)))
    }

    override fun paint(g: Graphics?) {
      super.paint(g)
      val editor = editor as EditorEx
      val leftInset = editor.scrollPane.border.getBorderInsets(editor.scrollPane).left
      val xStart = leftInset - ICON.iconWidth - ICON_RIGHT_MARGIN
      ICON.paintIcon(this, g, xStart, (size.height - ICON.iconHeight) / 2)
    }
  }

  var searchText: String
    get() = searchField.text
    set(value) {
      searchField.text = value
      updateQuery()
    }

  init {
    setContent(searchField)
    border = JBUI.Borders.empty(2)
  }

  private fun updateQuery() {
    model.query = GithubPullRequestSearchQuery.parseFromString(searchField.text)
  }

  private inner class SearchCompletionProvider : TextFieldCompletionProviderDumbAware(true) {
    private val addColonInsertHandler = object : InsertHandler<LookupElement> {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (context.completionChar == ':') return
        val editor = context.editor
        if (!isAtColon(context)) {
          EditorModificationUtil.insertStringAtCaret(editor, ":")
          context.commitDocument()
        }
        autoPopupController.autoPopupMemberLookup(editor, null)
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
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.state)
                            .withTailText(":" + GithubIssueState.values().joinToString { it.name }, true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.author)
                            .withTailText(":username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.assignee)
                            .withTailText(":username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.after)
                            .withTailText(":yyyy-MM-dd", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.before)
                            .withTailText(":yyyy-MM-dd", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.sortBy)
                            .withTailText(":" + GithubIssueSearchSort.values().joinToString { it.name }, true)
                            .withInsertHandler(addColonInsertHandler))
      }
      else when {
        qualifierName.equals(GithubPullRequestSearchQuery.QualifierName.state.name, true) -> {
          for (state in GithubIssueState.values()) {
            result.addElement(LookupElementBuilder.create(state.name))
          }
        }
        qualifierName.equals(GithubPullRequestSearchQuery.QualifierName.sortBy.name, true) -> {
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