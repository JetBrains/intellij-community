// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.collaboration.snippets.PathHandlingMode
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor.Factory.Companion.getInstance
import org.jetbrains.plugins.github.api.GithubApiRequests.Gists.create
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubGist
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubCreateGistDialog
import org.jetbrains.plugins.github.util.GHCompatibilityUtil.getOrRequestToken
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubSettings
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

@Service(Service.Level.PROJECT)
class GithubCreateGistService(
  private val project: Project,
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
    if (!dialog.showAndGet()) {
      return
    }
    settings.setPrivateGist(dialog.isSecret)
    settings.setOpenInBrowserGist(dialog.isOpenInBrowser)
    settings.setCopyURLGist(dialog.isCopyURL)

    val account = Objects.requireNonNull<GithubAccount?>(dialog.account)

    val url = Ref<String?>()
    object : Task.Backgroundable(project, GithubBundle.message("create.gist.process")) {
      override fun run(indicator: ProgressIndicator) {
        val token = getOrRequestToken(account, project, GHLoginSource.GIST)
        if (token == null) return
        val requestExecutor = getInstance().create(account.server, token)

        var contents = GithubGistContentsCollector.collectContents(project, editor, file, files)

        val fileNameExtractor: suspend (VirtualFile) -> String =
          PathHandlingMode.getFileNameExtractor(project, files.toList(), dialog.pathMode!!)
        contents = renameContents(contents, files, fileNameExtractor)

        val gistUrl = createGist(project, requestExecutor, indicator, account.server,
                                 contents, dialog.isSecret, dialog.description, dialog.fileName)
        url.set(gistUrl)
      }

      override fun onSuccess() {
        if (url.isNull) {
          return
        }
        if (dialog.isCopyURL) {
          val stringSelection = StringSelection(url.get())
          CopyPasteManager.getInstance().setContents(stringSelection)
        }
        if (dialog.isOpenInBrowser) {
          BrowserUtil.browse(url.get()!!)
        }
        else {
          GithubNotifications
            .showInfoURL(project,
                         GithubNotificationIdsHolder.GIST_CREATED,
                         GithubBundle.message("create.gist.success"),
                         GithubBundle.message("create.gist.url"), url.get()!!)
        }
      }
    }.queue()
  }

  @VisibleForTesting
  fun createGist(
    project: Project,
    executor: GithubApiRequestExecutor,
    indicator: ProgressIndicator,
    server: GithubServerPath,
    contents: List<FileContent>,
    isSecret: Boolean,
    description: String,
    filename: String?,
  ): String? {
    var contents = contents
    if (contents.isEmpty()) {
      GithubNotifications.showWarning(project,
                                      GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                      GithubBundle.message("cannot.create.gist"),
                                      GithubBundle.message("create.gist.error.empty"))
      return null
    }
    if (contents.size == 1 && filename != null) {
      val entry: FileContent = contents.iterator().next()
      contents = mutableListOf(FileContent(filename, entry.content))
    }
    try {
      return executor.execute(indicator, create(server, contents, description, !isSecret)).getHtmlUrl()
    }
    catch (e: IOException) {
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                    GithubBundle.message("cannot.create.gist"),
                                    e)
      return null
    }
  }

  private fun renameContents(
    contents: List<FileContent>,
    files: Array<VirtualFile>,
    fileNameExtractor: suspend (VirtualFile) -> String,
  ): List<FileContent> {
    val renamedContents = mutableListOf<FileContent>()

    if (files.isNotEmpty()) {
      for (i in files.indices) {
        val originalContent = contents[i]
        try {
          val id = i
          val newFileName = runBlocking(EmptyCoroutineContext) { fileNameExtractor(files[id]) }
          renamedContents.add(FileContent(newFileName.replace("/", "\\"), originalContent.content))
        }
        catch (e: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }
      }
    }
    if (renamedContents.isEmpty()) { // case of terminal
      return contents
    }
    return renamedContents
  }
}