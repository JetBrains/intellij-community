// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.*
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.data.GitLabSnippetBlobActionEnum
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetBlobAction
import org.jetbrains.plugins.gitlab.api.getResultOrThrow
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabSelectorErrorStatusPresenter.Companion.isAuthorizationException
import org.jetbrains.plugins.gitlab.mergerequest.util.localizedMessageOrClassName
import org.jetbrains.plugins.gitlab.snippets.GitLabSnippetService.Companion.GL_SNIPPET_FILES_LIMIT
import org.jetbrains.plugins.gitlab.snippets.PathHandlingMode.Companion.getFileNameExtractor
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.SnippetAction.*
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.logSnippetActionExecuted
import java.awt.datatransfer.StringSelection

/**
 * Service required to get a [CoroutineScope] when performing [GitLabCreateSnippetAction].
 */
@Service(Service.Level.PROJECT)
internal class GitLabSnippetService(private val project: Project, private val serviceScope: CoroutineScope) {
  private companion object {
    const val GL_SNIPPET_FILES_LIMIT = 10

    const val GL_NOTIFICATION_CREATE_SNIPPET_SUCCESS = "gitlab.snippet.create.action.success"
    const val GL_NOTIFICATION_CREATE_SNIPPET_ERROR = "gitlab.snippet.create.action.error"
  }

  /**
   * Performs the ['Create Snippet' action][GitLabCreateSnippetAction] by showing the 'Create Snippet' dialog
   * and performing the GitLab API request to create the final snippet.
   */
  fun performCreateSnippetAction(editor: Editor?, selectedFile: VirtualFile?, selectedFiles: List<VirtualFile>?) {
    serviceScope.launch {
      try {
        val files = collectNonBinaryFiles(editor?.virtualFile?.let(::listOf)
                                          ?: selectedFiles
                                          ?: listOfNotNull(selectedFile))
        val initialTitle = selectedFile?.name
                           ?: selectedFiles?.firstOrNull()?.name
                           ?: files.first().name

        val contents = async(Dispatchers.IO) {
          readAction {
            collectContents(editor, files) ?: listOf()
          }
        }

        val accountManager = service<GitLabAccountManager>()
        val apiManager = service<GitLabApiManager>()

        // Ensure at least some account is available
        if (accountManager.accountsState.value.isEmpty()) {
          if (!attemptLogin(accountManager)) return@launch
        }

        val result = showDialog(initialTitle, contents, apiManager, files) ?: return@launch

        withBackgroundProgress(project, message("snippet.create.action.progress"), true) {
          createSnippet(accountManager, apiManager, result, files)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        logSnippetActionExecuted(project, CREATE_ERRORED)

        VcsNotifier.getInstance(project)
          .notifyError(GL_NOTIFICATION_CREATE_SNIPPET_ERROR,
                       message("snippet.create.action.error.title"),
                       e.localizedMessageOrClassName())
      }
    }
  }

  /**
   * Attempts an initial login and stores the login details in the account manager.
   */
  private suspend fun attemptLogin(accountManager: GitLabAccountManager): Boolean {
    return coroutineScope {
      async(Dispatchers.Main) {
        val (account, token) = GitLabLoginUtil.logInViaToken(project, null) { server, name ->
          GitLabLoginUtil.isAccountUnique(accountManager.accountsState.value, server, name)
        }.asSafely<LoginResult.Success>() ?: return@async false

        accountManager.updateAccount(account, token)
        true
      }.await()
    }
  }

  /**
   * Gives the user a dialog to re-attempt login after no token could be found for a certain account.
   * If the user could still not be authenticated could be done, `null` is returned.
   */
  private suspend fun reattemptLogin(apiManager: GitLabApiManager,
                                     accountManager: GitLabAccountManager,
                                     result: GitLabCreateSnippetResult): GitLabApi? {
    return coroutineScope {
      async(Dispatchers.EDT) {
        val loginResult = GitLabLoginUtil.updateToken(project, null, result.account) { server, name ->
          GitLabLoginUtil.isAccountUnique(accountManager.accountsState.value, server, name)
        }.asSafely<LoginResult.Success>() ?: return@async null

        accountManager.updateAccount(result.account, loginResult.token)
        apiManager.getClient(result.account.server, loginResult.token)
      }.await()
    }
  }

  /**
   * Gets and verifies an API Client that can be used to create a snippet with.
   * If no token is registered, the user is prompted to attempt a login. If the token is present,
   * but it turns out to be invalid, the user is also prompted to attempt a login.
   *
   * The result is either an API Client that can be used to call API endpoints, or `null` if the
   * user gives up.
   */
  private suspend fun getAndVerifyApi(accountManager: GitLabAccountManager,
                                      apiManager: GitLabApiManager,
                                      result: GitLabCreateSnippetResult): GitLabApi? {
    // Fetch token, if no token is present, re-login
    val server = result.account.server
    val api = accountManager.findCredentials(result.account)?.let { apiManager.getClient(server, it) }
              ?: reattemptLogin(apiManager, accountManager, result)
              ?: return null

    // If a token is present, we check that it is valid with a test request. Reattempt login if token is invalid.
    runCatchingUser { api.graphQL.getCurrentUser() }.onFailure {
      when {
        isAuthorizationException(it) -> return reattemptLogin(apiManager, accountManager, result)
        else -> throw it
      }
    }

    return api
  }

  /**
   * Creates the snippet on the GitLab server through the GQL API of GitLab.
   *
   * @param result Provides the inputs the user gave for submitting a new snippet.
   * @param files The files the user selected, or the file that is in editor.
   */
  private suspend fun createSnippet(accountManager: GitLabAccountManager,
                                    apiManager: GitLabApiManager,
                                    result: GitLabCreateSnippetResult,
                                    files: List<VirtualFile>) {
    // Process result by creating the snippet, copying url, etc.
    val data = result.data
    val api = getAndVerifyApi(accountManager, apiManager, result) ?: return

    val fileNameExtractor = getFileNameExtractor(project, files, data.pathHandlingMode)

    val snippetBlobActions = result.nonEmptyContents.map { glContents ->
      GitLabSnippetBlobAction(
        GitLabSnippetBlobActionEnum.create,
        glContents.capturedContents,
        glContents.file?.let { fileNameExtractor(it) } ?: "",
        null
      )
    }

    val httpResult = api.graphQL.createSnippet(
      result.onProject,
      data.title,
      data.description,
      if (data.isPrivate) GitLabVisibilityLevel.private else GitLabVisibilityLevel.public,
      snippetBlobActions
    )
    val snippet = httpResult.getResultOrThrow()

    val url = snippet.webUrl
    if (data.isCopyUrl) {
      CopyPasteManager.getInstance().setContents(StringSelection(url))
    }
    if (data.isOpenInBrowser) {
      BrowserUtil.browse(url)
    }

    logSnippetActionExecuted(project, CREATE_CREATED)

    if (data.isOpenInBrowser) return

    VcsNotifier.getInstance(project)
      .notifyMinorInfo(GL_NOTIFICATION_CREATE_SNIPPET_SUCCESS,
                       message("snippet.create.action.success.title"),
                       HtmlChunk.link(url, message("snippet.create.action.success.description")).toString())
      .setListener(NotificationListener.UrlOpeningListener(false))
  }

  /**
   * Checks whether the user should be able to see and click the 'Create Snippet' button.
   */
  fun canCreateSnippet(editor: Editor?, selectedFile: VirtualFile?, selectedFiles: List<VirtualFile>?): Boolean {
    if (project.isDefault) {
      return false
    }

    if (editor != null) {
      return editor.document.textLength != 0
    }

    val files = collectNonBinaryFiles(selectedFiles
                                      ?: listOfNotNull(selectedFile))

    return files.size in 1..GL_SNIPPET_FILES_LIMIT
  }

  /**
   * Shows the 'Create Snippet' dialog and returns a view-model representing the final state of the user inputs.
   */
  private suspend fun showDialog(initialTitle: String,
                                 contents: Deferred<List<GitLabSnippetFileContents>>,
                                 apiManager: GitLabApiManager,
                                 files: List<VirtualFile>): GitLabCreateSnippetResult? =
    coroutineScope {
      val availablePathHandlingModes =
        PathHandlingMode.entries.filter {
          val extractor = getFileNameExtractor(project, files, it)
          files.map { file -> extractor(file) }.toSet().size == files.size // Check that there are no duplicates when mapped
        }

      val vmCs = childScope()
      val vm = GitLabCreateSnippetViewModel(
        vmCs,
        project,
        service<GitLabAccountManager>(),
        apiManager,
        availablePathHandlingModes.toSet(),
        contents,
        GitLabCreateSnippetViewModelData(
          initialTitle,
          "",

          true,
          true,
          false,

          PathHandlingMode.RelativePaths
        )
      )

      val dialogIsOk = async(Dispatchers.Main) {
        val dialog = GitLabCreateSnippetComponentFactory
          .create(this, project, vm)

        dialog.showAndGet()
      }.await()

      logSnippetActionExecuted(project, if (dialogIsOk) CREATE_OK else CREATE_CANCEL)

      // Await result of dialog
      if (!dialogIsOk) {
        return@coroutineScope null
      }

      val result = vm.toResult()
      vmCs.cancelAndJoinSilently()

      result
    }

  /**
   * Collects all non-binary files under the given files or directories, including those files
   * or directories. Files are collected and returned as a list, which is guaranteed to contain
   * at most [GL_SNIPPET_FILES_LIMIT] number of elements.
   *
   * @return The list of collected files with at most [GL_SNIPPET_FILES_LIMIT] elements.
   */
  private fun collectNonBinaryFiles(files: List<VirtualFile>): List<VirtualFile> {
    val collection = mutableSetOf<VirtualFile>()
    files.forEach {
      if (it.collectNonBinaryFilesImpl(collection)) {
        return collection.toList()
      }
    }
    return collection.toList()
  }

  /**
   * Collects all non-binary files under this file/directory, including this file if this [VirtualFile]
   * is indeed a non-binary file. Files are collected in [collection], which is guaranteed to contain
   * exactly [GL_SNIPPET_FILES_LIMIT]+1 number of elements when `true` is returned, or less than or equal
   * to [GL_SNIPPET_FILES_LIMIT] number of elements when `false` is returned.
   *
   * @return `true` if the limit is reached, `false`, if not.
   */
  private fun VirtualFile.collectNonBinaryFilesImpl(collection: MutableSet<VirtualFile>): Boolean {
    val fileType = runCatching {
      this.fileType
    }

    if (fileType.isFailure || (isFile && fileType.getOrNull()?.isBinary == true)
        || collection.size > GL_SNIPPET_FILES_LIMIT || isRecursiveOrCircularSymlink) {
      return collection.size > GL_SNIPPET_FILES_LIMIT
    }

    if (this.isFile) {
      collection += this
    }

    children?.forEach {
      if (it.collectNonBinaryFilesImpl(collection)) {
        return true
      }
    }

    return false
  }

  /**
   * Collects the contents from [Editor], or list of [files][VirtualFile], or [file][VirtualFile] in that order.
   * The first content holder that is not `null` will be used.
   */
  private fun collectContents(editor: Editor?,
                              files: List<VirtualFile>?): List<GitLabSnippetFileContents>? =
    if (editor != null) {
      editor.collectContents()?.let(::listOf)
    }
    else {
      files?.map { f ->
        f.collectContents() ?: GitLabSnippetFileContents(f, "")
      } ?: listOf()
    }

  /**
   * Collects the selected contents of a file in the [Editor] as [GitLabSnippetFileContents].
   * If no selection is active in the [Editor], the user right-clicked in the file, so the user is assumed
   * to want to make a snippet of the entire file, so file contents are used.
   *
   * @return `null` if no contents could be collected because there is no active selection or a read
   * could not be completed, [GitLabSnippetFileContents] representing the selection or file otherwise.
   */
  private fun Editor.collectContents(): GitLabSnippetFileContents? {
    val content = selectionModel.selectedText?.ifEmpty { null }
                  ?: document.text.ifEmpty { null }
                  ?: return null

    return GitLabSnippetFileContents(virtualFile, content)
  }

  /**
   * Collects the full contents of a [file][VirtualFile] as [GitLabSnippetFileContents].
   *
   * @return `null` if no contents could be collected because the file is empty or a read could not
   * be completed, [GitLabSnippetFileContents] representing the entire file contents otherwise.
   */
  private fun VirtualFile.collectContents(): GitLabSnippetFileContents? {
    val content = String(contentsToByteArray(), charset)
      .ifEmpty { return null }

    return GitLabSnippetFileContents(this, content)
  }
}