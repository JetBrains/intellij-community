// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchSort
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class GithubPullRequestSearchPanel(project: Project,
                                            private val autoPopupController: AutoPopupController,
                                            private val holder: GithubPullRequestSearchQueryHolder)
  : BorderLayoutPanel(), Disposable {

  private val searchField = object : TextFieldWithCompletion(project, SearchCompletionProvider(), "", true, true, false, false) {

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
      editor.setBorder(JBUI.Borders.empty(6, 5))
    }

    override fun updateUI() {
      super.updateUI()
      GithubUIUtil.setTransparentRecursively(this)
    }
  }

  init {
    val icon = JBLabel(AllIcons.Actions.Find).apply {
      border = JBUI.Borders.emptyLeft(5)
    }
    addToLeft(icon)
    addToCenter(searchField)
    holder.addQueryChangeListener(this) {
      searchField.text = holder.query.toString()
    }
  }

  private fun updateQuery() {
    holder.query = GithubPullRequestSearchQuery.parseFromString(searchField.text)
  }

  override fun updateUI() {
    super.updateUI()
    background = UIUtil.getListBackground()
  }

  override fun dispose() {}

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
                            .withTailText(":")
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.author)
                            .withTailText(":")
                            .withTypeText("username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.assignee)
                            .withTailText(":")
                            .withTypeText("username", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.after)
                            .withTailText(":")
                            .withTypeText("YYYY-MM-DD", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.before)
                            .withTailText(":")
                            .withTypeText("YYYY-MM-DD", true)
                            .withInsertHandler(addColonInsertHandler))
        result.addElement(LookupElementBuilder.create(GithubPullRequestSearchQuery.QualifierName.sortBy)
                            .withTailText(":")
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