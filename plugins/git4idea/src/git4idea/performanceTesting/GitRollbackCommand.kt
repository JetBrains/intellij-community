// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.RollbackWorker
import com.intellij.vcsUtil.RollbackUtil
import com.jetbrains.performancePlugin.commands.OpenFileCommand
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.toPromise

class GitRollbackCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "gitRollback"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val filePath = extractCommandArgument(PREFIX)
    withContext(Dispatchers.EDT) {
      val actionCallback = writeIntentReadAction {
        val file = OpenFileCommand.findFile(filePath, project) ?: throw IllegalArgumentException("There is no file  ${filePath}")
        val changes = ChangeListManager.getInstance(project).defaultChangeList.changes
        val fileChanges = changes.filter { it.virtualFile == file }
        FileDocumentManager.getInstance().saveAllDocuments()
        val operationName = RollbackUtil.getRollbackOperationName(project)
        val ac = ActionCallback()
        RollbackWorker(project, operationName, false)
          .doRollback(fileChanges, false, Runnable {
            ac.setDone()
          }, null)
        ac
      }
      actionCallback.toPromise().await()
    }
  }

  override fun getName(): String {
    return NAME
  }
}