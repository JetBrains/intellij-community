// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.snippets.PathHandlingMode
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor.Factory.Companion.getInstance
import org.jetbrains.plugins.github.api.GithubApiRequests.Gists.create
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubCreateGistDialog
import org.jetbrains.plugins.github.util.GHCompatibilityUtil.getOrRequestToken
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubSettings
import java.awt.datatransfer.StringSelection
import java.io.IOException

@Service(Service.Level.PROJECT)
class GithubCreateGistService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  fun createGistAction(
    editor: Editor?,
    file: VirtualFile?,
    files: Array<VirtualFile>,
  ) {
    val settings = GithubSettings.getInstance()
    // Ask for description and other params
    val fileName = GithubGistContentsCollector.getGistFileName(editor, files)
    val dialog = GithubCreateGistDialog(project,
                                        fileName,
                                        settings.isPrivateGist(),
                                        settings.isOpenInBrowserGist(),
                                        settings.isCopyURLGist())
    if (!dialog.showAndGet()) return

    settings.isPrivateGist = dialog.isSecret
    settings.isOpenInBrowserGist = dialog.isOpenInBrowser
    settings.isCopyURLGist = dialog.isCopyURL

    val account = dialog.account!!

    cs.launch {
      withBackgroundProgress(project, GithubBundle.message("create.gist.process"), cancellable = true) {
        val token = getOrRequestToken(account, project, GHLoginSource.GIST)
        if (token == null) return@withBackgroundProgress
        val requestExecutor = getInstance().create(account.server, token)

        val contents = GithubGistContentsCollector.collectContents(project, editor, file, files).let {
          renameContents(it, files, dialog.pathMode!!)
        }

        val gistUrl = createGist(project, requestExecutor, account.server,
                                 contents, dialog.isSecret, dialog.description, dialog.fileName)

        // onSuccess
        if (gistUrl == null) {
          return@withBackgroundProgress
        }
        if (dialog.isCopyURL) {
          val stringSelection = StringSelection(gistUrl)
          CopyPasteManager.getInstance().setContents(stringSelection)
        }
        if (dialog.isOpenInBrowser) {
          BrowserUtil.browse(gistUrl)
        }
        else {
          GithubNotifications.showInfoURL(
            project,
            GithubNotificationIdsHolder.GIST_CREATED,
            GithubBundle.message("create.gist.success"),
            GithubBundle.message("create.gist.url"), gistUrl
          )
        }
      }
    }
  }

  @VisibleForTesting
  suspend fun createGist(
    project: Project,
    executor: GithubApiRequestExecutor,
    server: GithubServerPath,
    contents: List<FileContent>,
    isSecret: Boolean,
    description: String,
    filename: String?,
  ): String? {
    if (contents.isEmpty()) {
      GithubNotifications.showWarning(
        project,
        GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
        GithubBundle.message("cannot.create.gist"),
        GithubBundle.message("create.gist.error.empty")
      )
      return null
    }

    val contents = if (contents.size == 1 && filename != null) {
      mutableListOf(FileContent(filename, contents.first().content))
    }
    else contents

    return try {
      executor.executeSuspend(create(server, contents, description, !isSecret)).htmlUrl
    }
    catch (e: IOException) {
      GithubNotifications.showError(
        project,
        GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
        GithubBundle.message("cannot.create.gist"),
        e
      )
      null
    }
  }

  private suspend fun renameContents(
    contents: List<FileContent>,
    files: Array<VirtualFile>,
    pathHandlingMode: PathHandlingMode,
  ): List<FileContent> {
    val fileNameExtractor = PathHandlingMode.getFileNameExtractor(project, files.toList(), pathHandlingMode)

    return contents.zip(files) { originalContent, file ->
      val newFileName = fileNameExtractor(file)
      FileContent(newFileName.replace("/", "\\"), originalContent.content)
    }
  }
}