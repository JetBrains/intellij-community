// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.text.DateFormatUtil.formatPrettyDateTime
import org.jetbrains.annotations.Nls
import java.util.function.Function
import javax.swing.JComponent

internal class OutdatedVersionNotifier : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val cache = CommittedChangesCache.getInstanceIfCreated(project) ?: return null
    val (incomingChangeList, incomingChange) = cache.getIncomingChangeList(file) ?: return null
    if (!isIncomingChangesAvailable(incomingChangeList.vcs)) return null

    return Function {
      createOutdatedVersionPanel(incomingChangeList, incomingChange, it)
    }
  }

  internal class IncomingChangesListener(private val project: Project) : CommittedChangesListener {
    override fun incomingChangesUpdated(receivedChanges: List<CommittedChangeList>?) {
      val cache = CommittedChangesCache.getInstance(project)

      if (cache.cachedIncomingChanges != null) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
      else {
        cache.hasCachesForAnyRoot { hasCaches ->
          if (!hasCaches) return@hasCachesForAnyRoot

          // we do not use `consumer` as `incomingChangesUpdated` will be fired again after incoming changes loading
          cache.loadIncomingChangesAsync(null, true)
        }
      }
    }

    override fun changesCleared() = EditorNotifications.getInstance(project).updateAllNotifications()
  }
}

private fun createOutdatedVersionPanel(changeList: CommittedChangeList, change: Change, fileEditor: FileEditor): EditorNotificationPanel =
  EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
    createActionLabel(message("outdated.version.show.diff.action"), "Compare.LastVersion")
    createActionLabel(message("outdated.version.update.project.action"), "Vcs.UpdateProject")
    text = getOutdatedVersionText(changeList, change)
  }

private fun getOutdatedVersionText(changeList: CommittedChangeList, change: Change): @Nls String {
  val formattedDate = formatPrettyDateTime(changeList.commitDate)
  return message("outdated.version.text", changeList.committerName, formattedDate, changeList.comment.getSubject(),
                 if (change.type == Change.Type.DELETED) 1 else 0)
}

private fun String.getSubject(): String {
  val newLineIndex = indexOf('\n')
  return if (newLineIndex < 0) this else substring(0, newLineIndex).trim() + "..."
}