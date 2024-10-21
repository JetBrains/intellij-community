// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.FindBundle
import com.intellij.find.FindUsagesSettings
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.UIBundle
import com.intellij.ui.table.JBTable
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains

abstract class DeclarationAndUsagesLesson
  : KLesson("Declaration and usages", LessonsBundle.message("declaration.and.usages.lesson.name")) {
  abstract fun LessonContext.setInitialPosition()
  abstract override val sampleFilePath: String
  abstract val entityName: String

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      sdkConfigurationTasks()

      configurationTasks()

      setInitialPosition()

      prepareRuntimeTask {
        val focusManager = IdeFocusManager.getInstance(project)
        if (focusManager.focusOwner != editor.contentComponent) {
          focusManager.requestFocus(editor.contentComponent, true)
        }
      }

      task("GotoDeclaration") {
        text(LessonsBundle.message("declaration.and.usages.jump.to.declaration", action(it)))
        trigger(it, { state() }) { before, _ ->
          before != null && !isInsidePsi(before.target.navigationElement, before.position)
        }
        restoreIfModifiedOrMoved()
        test { actions(it) }
      }

      task("GotoDeclaration") { actionId ->
        text(LessonsBundle.message("declaration.and.usages.show.usages", action(actionId)))
        stateCheck l@{
          val curEditor = editor
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(curEditor.document) ?: return@l false
          val offset = curEditor.caretModel.offset
          val element = psiFile.findElementAt(offset) ?: return@l false
          val parentExpr = getParentExpression(element) ?: return@l false
          parentExpr.text.endsWith(entityName)
        }
        //restoreIfModifiedOrMoved()
        test {
          actions(actionId)
          ideFrame {
            waitComponent(JBTable::class.java, "ShowUsagesTable")
            invokeActionViaShortcut("ENTER")
          }
        }
      }

      task("FindUsages") {
        before {
          closeAllFindTabs()
        }
        text(LessonsBundle.message("declaration.and.usages.find.usages", action(it)))

        triggerAndFullHighlight().component { ui: BaseLabel ->
          ui.javaClass.simpleName == "ContentTabLabel" && ui.text.isToStringContains(entityName)
        }
        restoreIfModifiedOrMoved()
        test {
          actions(it)
        }
      }

      val pinTabText = UIBundle.message("tabbed.pane.pin.tab.action.name")
      task {
        test {
          ideFrame {
            previous.ui?.let { usagesTab -> jComponent(usagesTab).rightClick() }
          }
        }
        triggerAndBorderHighlight().component { ui: ActionMenuItem ->
          ui.text.isToStringContains(pinTabText)
        }
        restoreByUi()
        text(LessonsBundle.message("declaration.and.usages.pin.motivation", strong(UIBundle.message("tool.window.name.find"))))
        text(LessonsBundle.message("declaration.and.usages.right.click.tab",
                                   strong(FindBundle.message("find.usages.of.element.in.scope.panel.title",
                                                             entityName, FindUsagesSettings.getInstance().defaultScopeName))))
      }

      task("PinToolwindowTab") {
        trigger(it)
        restoreByUi()
        text(LessonsBundle.message("declaration.and.usages.select.pin.item", strong(pinTabText)))
        test {
          ideFrame {
            jComponent(previous.ui!!).click()
          }
        }
      }

      task("HideActiveWindow") {
        text(LessonsBundle.message("declaration.and.usages.hide.view", action(it)))
        checkToolWindowState("Find", false)
        test { actions(it) }
      }

      actionTask("ActivateFindToolWindow") {
        LessonsBundle.message("declaration.and.usages.open.find.view",
                              action(it), strong(UIBundle.message("tool.window.name.find")))
      }
    }

  protected open fun LessonContext.configurationTasks() {}

  protected abstract fun getParentExpression(element: PsiElement): PsiElement?

  private fun TaskRuntimeContext.state(): MyInfo? {
    val flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED

    val currentEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null

    val target = TargetElementUtil.findTargetElement(currentEditor, flags) ?: return null

    val file = PsiDocumentManager.getInstance(project).getPsiFile(currentEditor.document) ?: return null
    val position = MyPosition(file,
                              currentEditor.caretModel.offset)

    return MyInfo(target, position)
  }

  private fun isInsidePsi(psi: PsiElement, position: MyPosition): Boolean {
    return psi.containingFile == position.file && psi.textRange.contains(position.offset)
  }

  private data class MyInfo(val target: PsiElement, val position: MyPosition)

  private data class MyPosition(val file: PsiFile, val offset: Int)

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("declaration.and.usages.help.link"),
         LessonUtil.getHelpLink("navigating-through-the-source-code.html#go_to_declaration")),
  )
}
