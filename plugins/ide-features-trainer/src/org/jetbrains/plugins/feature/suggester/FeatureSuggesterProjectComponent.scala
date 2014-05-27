package org.jetbrains.plugins.feature.suggester

import java.awt.Point

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.ide.IdeTooltipManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.actions.{BackspaceAction, DeleteAction}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.ui.LightweightHint
import org.jetbrains.plugins.feature.suggester.changes._
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings

import scala.collection.mutable.ListBuffer

/**
 * @author Alefas
 * @since 23.05.13
 */
class FeatureSuggesterProjectComponent(project: Project) extends ProjectComponent {
  private val actionsList = new ListBuffer[UserAction]()
  private val anActionList = new ListBuffer[UserAnAction]()
  private val ACTION_NUMBER = 100

  private def addAction(action: UserAction) {
    if (action.parent == null) return
    if (!action.parent.getLanguage.is(JavaLanguage.INSTANCE)) return //todo: add other languages for this framework
    actionsList += action
    if (actionsList.size > ACTION_NUMBER) actionsList.remove(0)
    val actions = actionsList.toList
    for (suggester <- FeatureSuggester.getAllSuggesters if isEnabled(suggester)) {
      suggester.getSuggestion(actions, anActionList.toList) match {
        case NoSuggestion => //do nothing
        case FeatureUsageSuggestion => countFeatureUsage(suggester)
        case PopupSuggestion(message) =>
          val file = action.parent.getContainingFile
          val virtualFile = file.getVirtualFile
          if (virtualFile == null) return
          val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
          if (editor == null) return
          if (suggester.needToClearLookup()) {
            //todo: this is hack to avoid exception in spection completion case
            LookupManager.getInstance(project).asInstanceOf[LookupManagerImpl].clearLookup()
          }
          val label = HintUtil.createQuestionLabel(message)
          val hint: LightweightHint = new PatchedLightweightHint(label) //todo: this is hack to avoid hiding on parameter info popup
          val hintManager = HintManager.getInstance().asInstanceOf[HintManagerImpl]
          val p: Point = hintManager.getHintPosition(hint, editor, HintManager.ABOVE)
          IdeTooltipManager.getInstance().hideCurrentNow(false)
          hintManager.showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, false)
          return
      }
    }
  }

  private def addAnAction(action: UserAnAction) {
    anActionList += action
    if (actionsList.size > ACTION_NUMBER) actionsList.remove(0)
  }

  private def isEnabled(suggester: FeatureSuggester): Boolean = {
    FeatureSuggesterSettings.getInstance().isEnabled(suggester.getId)
  }

  private def countFeatureUsage(suggester: FeatureSuggester) {
    //todo: enable/disable functionality
  }

  def getComponentName: String = "Feature suggester project component"

  def projectOpened() {}

  def projectClosed() {}

  def initComponent() {
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter {
      override def propertyChanged(event: PsiTreeChangeEvent) {
        addAction(PropertyChangedAction(event.getParent))
      }

      override def childRemoved(event: PsiTreeChangeEvent) {
        addAction(ChildRemovedAction(event.getParent, event.getChild))
      }

      override def childReplaced(event: PsiTreeChangeEvent) {
        addAction(ChildReplacedAction(event.getParent, event.getNewChild, event.getOldChild))
      }

      override def childAdded(event: PsiTreeChangeEvent) {
        addAction(ChildAddedAction(event.getParent, event.getChild))
      }

      override def childrenChanged(event: PsiTreeChangeEvent) {
        addAction(ChildrenChangedAction(event.getParent))
      }

      override def childMoved(event: PsiTreeChangeEvent) {
        addAction(ChildMovedAction(event.getNewParent, event.getChild, event.getOldParent))
      }
    })

    ActionManager.getInstance().addAnActionListener(new AnActionListener {
      def afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {}

      def beforeEditorTyping(c: Char, dataContext: DataContext) {}

      def beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        action match {
          case b: BackspaceAction =>
            val editor = CommonDataKeys.EDITOR.getData(dataContext)
            if (editor != null) {
              val selectedText = Option(editor.getSelectionModel.getSelectedText).getOrElse("")
              addAnAction(new changes.BackspaceAction(selectedText, System.currentTimeMillis()))
            }
          case d: DeleteAction =>
            val editor = CommonDataKeys.EDITOR.getData(dataContext)
            if (editor != null) {
              val selectedText = Option(editor.getSelectionModel.getSelectedText).getOrElse("")
              addAnAction(new changes.BackspaceAction(selectedText, System.currentTimeMillis()))
              //todo: it should have own DeleteAction...
            }
          case _ =>
        }
      }
    })
  }

  def disposeComponent() {}
}
