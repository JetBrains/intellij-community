// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.navigation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testGuiFramework.framework.GuiTestUtil.shortcut
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.UIBundle
import com.intellij.ui.table.JBTable
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.lesson.kimpl.closeAllFindTabs

abstract class DeclarationAndUsagesLesson(module: Module, lang: String)
  : KLesson("Declaration and usages", LessonsBundle.message("declaration.and.usages.lesson.name"), module, lang) {
  abstract fun LessonContext.setInitialPosition()
  abstract override val existedFile: String

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      setInitialPosition()

      task("GotoDeclaration") {
        text(LessonsBundle.message("declaration.and.usages.jump.to.declaration", action(it)))
        trigger(it, { state() }) { before, _ ->
          before != null && !isInsidePsi(before.target.navigationElement, before.position)
        }
        restoreIfModifiedOrMoved()
        test { actions(it) }
      }

      task("GotoDeclaration") {
        text(LessonsBundle.message("declaration.and.usages.show.usages", action(it)))
        trigger(it, { state() }) l@{ before, now ->
          if (before == null || now == null) {
            return@l false
          }

          val navigationElement = before.target.navigationElement
          return@l navigationElement == now.target.navigationElement &&
                   isInsidePsi(navigationElement, before.position) &&
                   !isInsidePsi(navigationElement, now.position)
        }
        restoreIfModifiedOrMoved()
        test {
          actions(it)
          ideFrame {
            waitComponent(JBTable::class.java, "ShowUsagesTable")
            shortcut(Key.ENTER)
          }
        }
      }

      task("FindUsages") {
        before {
          closeAllFindTabs()
        }
        text(LessonsBundle.message("declaration.and.usages.find.usages", action(it)))

        triggerByUiComponentAndHighlight { ui: BaseLabel ->
          ui.text?.contains(LearnBundle.message("usages.tab.name")) ?: false
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
        triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
          ui.text?.contains(pinTabText) ?: false
        }
        restoreByUi()
        text(LessonsBundle.message("declaration.and.usages.pin.motivation", strong(UIBundle.message("tool.window.name.find"))))
        text(LessonsBundle.message("declaration.and.usages.right.click.tab", strong(LearnBundle.message("usages.tab.name"))))
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

      actionTask("HideActiveWindow") {
        LessonsBundle.message("declaration.and.usages.hide.view", action(it))
      }

      actionTask("ActivateFindToolWindow") {
        LessonsBundle.message("declaration.and.usages.open.find.view",
                              action(it), strong(UIBundle.message("tool.window.name.find")))
      }
    }

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
}
