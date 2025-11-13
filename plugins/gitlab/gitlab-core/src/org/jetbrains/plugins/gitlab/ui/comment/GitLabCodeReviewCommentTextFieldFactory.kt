// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.icons.AllIcons
import com.intellij.ide.PasteProvider
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.JComponent


private val COMMIT_FILE_PASTE_PROVIDER: DataKey<PasteProvider> = create("GitLab.Comment.PasteProvider")

object GitLabCodeReviewCommentTextFieldFactory {

  fun createIn(
    cs: CoroutineScope,
    vm: GitLabCodeReviewSubmittableTextViewModel,
    actions: CommentInputActionsComponentFactory.Config,
    icon: CommentTextFieldFactory.IconConfig? = null,
  ): JComponent {

    val canUploadFile = Registry.`is`("gitlab.merge.requests.file.upload.enabled") && vm.canUploadFile()

    val uploadFileAction = object : AnAction(GitLabBundle.message("action.GitLab.Review.Upload.File.text"),
                                             GitLabBundle.message("action.GitLab.Review.Upload.File.description"),
                                             AllIcons.Actions.Upload) {
      override fun actionPerformed(e: AnActionEvent) {
        vm.uploadFile(null)
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = canUploadFile
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    val dropHandler = object: EditorDropHandler {
      override fun canHandleDrop(transferFlavors: Array<out DataFlavor>): Boolean {
        return canUploadFile && transferFlavors.contains(DataFlavor.javaFileListFlavor)
      }

      override fun handleDrop(t: Transferable, project: Project?, editorWindow: EditorWindow?) {
        val list = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
        list.filterIsInstance<File>().firstOrNull()?.let { file ->
          vm.uploadFile(file.toPath())
        }
      }
    }

    val editorComponent = CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon) { editor: Editor ->

      (editor as EditorImpl).setDropHandler(dropHandler)

      (editor as EditorEx).installPopupHandler(object : ContextMenuPopupHandler() {
        override fun getActionGroup(event: EditorMouseEvent): ActionGroup {
          return DefaultActionGroup(ActionManager.getInstance().getAction(IdeActions.GROUP_BASIC_EDITOR_POPUP),
                                    uploadFileAction)
        }
      })
    }

    // Paste Action support
    return UiDataProvider.wrapComponent(editorComponent) { sink ->
      sink[COMMIT_FILE_PASTE_PROVIDER] = object : PasteProvider {
        override fun performPaste(dataContext: DataContext) {
          val contents = CopyPasteManager.getInstance().getContents() ?: return
          FileCopyPasteUtil.getFileList(contents)?.firstOrNull()?.let {
            vm.uploadFile(it.toPath())
            return
          }
          CopyPasteManager.getInstance().getContents<Image>(DataFlavor.imageFlavor)?.let {
            vm.uploadImage(it)
            return
          }
        }

        override fun isPastePossible(dataContext: DataContext): Boolean {
          return true
        }

        override fun isPasteEnabled(dataContext: DataContext): Boolean {
          if (!canUploadFile) return false
          val contents = CopyPasteManager.getInstance().getContents() ?: return false
          return !FileCopyPasteUtil.getFileList(contents).isNullOrEmpty()
                 || contents.isDataFlavorSupported(DataFlavor.imageFlavor)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
      }
    }
  }
}

object GitLabEditableComponentFactory {
  fun wrapTextComponent(
    cs: CoroutineScope, component: JComponent, editVmFlow: Flow<GitLabCodeReviewTextEditingViewModel?>,
    afterSave: () -> Unit = {},
  ): JComponent =
    EditableComponentFactory.create(cs, component, editVmFlow) { editVm: GitLabCodeReviewTextEditingViewModel ->
      val actions = createEditActionsConfig(editVm, afterSave)
      GitLabCodeReviewCommentTextFieldFactory.createIn(cs, editVm, actions)
    }
}

private class CommitFilePasteProvider : PasteProvider {
  override fun performPaste(dataContext: DataContext) {
    dataContext.getData(COMMIT_FILE_PASTE_PROVIDER)?.performPaste(dataContext)
  }

  override fun isPasteEnabled(dataContext: DataContext): Boolean {
    dataContext.getData(COMMIT_FILE_PASTE_PROVIDER)?.let { return it.isPasteEnabled(dataContext) }
    return false
  }

  override fun isPastePossible(dataContext: DataContext): Boolean {
    dataContext.getData(COMMIT_FILE_PASTE_PROVIDER)?.let { return it.isPastePossible(dataContext) }
    return false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

