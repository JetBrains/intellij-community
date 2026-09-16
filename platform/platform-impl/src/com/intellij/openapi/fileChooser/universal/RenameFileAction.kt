// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ProxyShortcutSet
import com.intellij.openapi.fileChooser.universal.UniversalFileChooser.Panel.FileView
import com.intellij.openapi.fileChooser.universal.UniversalFileChooser.runOnEdt
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

/**
 * Renames the file that the active tab selects.
 *
 * [activeFileView] returns the tab that holds the selection, or `null` when the panel has no active tab.
 * The update reads the file system, so it runs on a background thread.
 *
 * The icon is [AllIcons.Actions.Edit], because the platform has no rename icon.
 *
 * The action takes the shortcut of the platform Rename action, so it follows the active keymap.
 * A caller must still register the action on a component with [registerCustomShortcutSet].
 */
internal class RenameFileAction(private val activeFileView: () -> FileView?) : AnAction(
  IdeBundle.message("universal.file.chooser.action.rename.text"),
  IdeBundle.message("universal.file.chooser.action.rename.description"),
  AllIcons.Actions.Edit
) {
  init {
    // ProxyShortcutSet reads the active keymap on every call, so a keymap change applies at once.
    shortcutSet = ProxyShortcutSet(IdeActions.ACTION_RENAME)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = activeFileView()?.canRenameSelectedFile() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    activeFileView()?.renameSelectedFile()
  }
}

/**
 * A rename needs a single selected file that is not a root, and a writable parent directory.
 * A POSIX rename changes the parent directory, so the permission of the file itself does not matter.
 * Call this on a background thread, because it reads the file system.
 */
internal fun FileView.canRenameSelectedFile(): Boolean {
  if (fileTree.getSelectedFiles().size != 1) return false
  val selected = fileTree.getSelectedFile() ?: return false
  if (roots.contains(selected.invariantSeparatorsPathString)) return false
  val parent = selected.parent ?: return false
  if (!Files.isWritable(parent)) return false
  return true
}

/**
 * Asks for a new name, then moves the selected file to it.
 * The dialog starts with the current name and keeps OK off until the name is valid.
 * The move runs on [Dispatchers.IO] through NIO, so it also works on a remote file system.
 * A failure of the move reports the reason, for example a read-only directory or a name clash.
 */
internal fun FileView.renameSelectedFile() {
  if (fileTree.getSelectedFiles().size != 1) return
  val selected = fileTree.getSelectedFile() ?: return
  if (roots.contains(selected.invariantSeparatorsPathString)) return
  val parent = selected.parent ?: return
  val oldName = selected.name
  val newName = Messages.showInputDialog(
    project,
    IdeBundle.message("universal.file.chooser.action.rename.prompt", oldName),
    IdeBundle.message("universal.file.chooser.action.rename.title"),
    Messages.getQuestionIcon(),
    oldName,
    NioFileNameValidator(parent)
  )?.trim() ?: return
  if (newName.isEmpty() || newName == oldName) return
  val target = runCatching { parent.resolve(newName) }.getOrNull() ?: return

  scope.launch {
    var failure: Exception? = null
    try {
      withBackgroundProgress(project, IdeBundle.message("universal.file.chooser.action.rename.progress.title", oldName)) {
        withContext(Dispatchers.IO) {
          Files.move(selected, target)
        }
      }
    }
    catch (e: CancellationException) {
      // The user cancelled the progress (or the panel was disposed): show the current state, then propagate.
      runOnEdt { fileTree.updateTree() }
      throw e
    }
    catch (e: Exception) {
      failure = e
    }
    val error = failure
    runOnEdt {
      fileTree.updateTree()
      if (error != null) {
        Messages.showErrorDialog(
          renameErrorMessage(error, newName),
          IdeBundle.message("universal.file.chooser.action.rename.title")
        )
      }
      else {
        fileTree.select(target, null)
      }
    }
  }
}

private fun renameErrorMessage(error: Exception, newName: String): @Nls String {
  if (error is FileAlreadyExistsException) {
    return IdeBundle.message("universal.file.chooser.action.rename.error.exists", newName)
  }
  @NlsSafe val reason = error.localizedMessage ?: error.message ?: error.javaClass.simpleName
  return reason
}
