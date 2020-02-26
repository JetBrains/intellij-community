// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

class GHPRDiffEditorGutterIconRendererFactoryImpl(private val inlaysManager: EditorComponentInlaysManager,
                                                  private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                                  private val lineLocationCalculator: (Int) -> Pair<Side, Int>?)
  : GHPRDiffEditorGutterIconRendererFactory {

  override fun createCommentRenderer(line: Int): GHPRAddCommentGutterIconRenderer = CreateCommentIconRenderer(line)

  private inner class CreateCommentIconRenderer(override val line: Int) : GHPRAddCommentGutterIconRenderer() {

    private var inlay: Pair<JComponent, Disposable>? = null

    override fun getClickAction(): DumbAwareAction = CreateCommentAction(line)
    override fun isNavigateAction() = true
    override fun getIcon(): Icon = AllIcons.General.InlineAdd
    override fun equals(other: Any?): Boolean = other is CreateCommentIconRenderer && line == other.line
    override fun hashCode(): Int = line.hashCode()

    private inner class CreateCommentAction(private val editorLine: Int) : DumbAwareAction() {

      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let { GithubUIUtil.focusPanel(it.first) } != null) return

        val (side, line) = lineLocationCalculator(editorLine) ?: return

        val component =
          componentFactory.createCommentComponent(side, line) {
            inlay?.let { Disposer.dispose(it.second) }
            inlay = null
          }
        val disposable = inlaysManager.insertAfter(editorLine, component) ?: return
        val newInlay = component to disposable
        component.registerKeyboardAction({
                                           disposable.let {
                                             Disposer.dispose(it)
                                             inlay = null
                                           }
                                         },
                                         KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        GithubUIUtil.focusPanel(component)

        inlay = newInlay
      }
    }

    override fun disposeInlay() {
      inlay?.second?.let { Disposer.dispose(it) }
    }
  }
}