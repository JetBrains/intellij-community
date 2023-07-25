// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.data.GitLabSnippetBlobActionEnum
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import org.jetbrains.plugins.gitlab.api.dto.GitLabSnippetBlobAction
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import java.awt.datatransfer.StringSelection

/**
 * Service required to get a [CoroutineScope] when performing [GitLabCreateSnippetAction].
 */
@Service(Service.Level.PROJECT)
class GitLabSnippetService(private val project: Project, private val serviceScope: CoroutineScope) {
  companion object {
    const val GL_SNIPPET_FILES_LIMIT = 10

    const val GL_NOTIFICATION_CREATE_SNIPPET_SUCCESS = "gitlab.snippet.create.action.success"
    const val GL_NOTIFICATION_CREATE_SNIPPET_ERROR = "gitlab.snippet.create.action.error"
  }

  /**
   * Gets the name of a snippet entry directly from the file name.
   */
  private val pathFromName: suspend (VirtualFile) -> String = { file -> file.name }

  /**
   * Performs the ['Create Snippet' action][GitLabCreateSnippetAction] by showing the 'Create Snippet' dialog
   * and performing the GitLab API request to create the final snippet.
   */
  fun performCreateSnippetAction(editor: Editor?, selectedFile: VirtualFile?, selectedFiles: List<VirtualFile>?) {
    serviceScope.launch {
      val cs = this

      val files = collectNonBinaryFiles(editor?.virtualFile?.let(::listOf)
                                        ?: selectedFiles
                                        ?: listOfNotNull(selectedFile))
      val vm = showDialog(cs, editor, files) ?: return@launch

      withBackgroundProgress(project, message("snippet.create.action.progress"), true) {
        try {
          createSnippet(vm, files)
        }
        catch (e: Exception) {
          VcsNotifier.getInstance(project)
            .notifyError(GL_NOTIFICATION_CREATE_SNIPPET_ERROR,
                         message("snippet.create.action.error.title"),
                         e.localizedMessage)
        }
      }
    }
  }

  /**
   * Creates the snippet on the GitLab server through the GQL API of GitLab.
   *
   * @param vm Provides the inputs the user gave for submitting a new snippet.
   * @param files The files the user selected, or the file that is in editor.
   */
  private suspend fun createSnippet(vm: GitLabCreateSnippetViewModel,
                                    files: List<VirtualFile>) {
    // Process result by creating the snippet, copying url, etc.
    val data = vm.data
    val api = vm.getApi() ?: throw IllegalStateException(message("snippet.create.action.error.no-api"))
    val server = vm.getServer() ?: throw IllegalStateException(message("snippet.create.action.error.no-server"))

    val contents = vm.contents.await()
    val fileNameExtractor = getFileNameExtractor(files, data.pathHandlingMode)

    val snippetBlobActions = contents.map { glContents ->
      GitLabSnippetBlobAction(
        GitLabSnippetBlobActionEnum.create,
        glContents.capturedContents,
        glContents.file?.let { fileNameExtractor(it) } ?: "",
        null
      )
    }

    val httpResult = api.graphQL.createSnippet(
      server,
      data.onProject,
      data.title,
      data.description,
      if (data.isPrivate) GitLabVisibilityLevel.private else GitLabVisibilityLevel.public,
      snippetBlobActions
    )
    val result = httpResult.body() ?: throw IllegalStateException(message("snippet.create.action.error.http-deserialization"))

    val snippet = result.value ?: throw IllegalStateException(message("snippet.create.action.error.no-snippet"))
    val url = snippet.webUrl
    if (data.isCopyUrl) {
      CopyPasteManager.getInstance().setContents(StringSelection(url))
    }
    if (data.isOpenInBrowser) {
      BrowserUtil.browse(url)
    }
    else {
      VcsNotifier.getInstance(project)
        .notifyInfo(GL_NOTIFICATION_CREATE_SNIPPET_SUCCESS, message("snippet.create.action.success.title"),
                    message("snippet.create.action.success.description", url))
    }
  }

  /**
   * Gets the file name extractor function for the given [PathHandlingMode] using the given set of [files][VirtualFile].
   * If the list of files are empty, the name selector used should not matter (and no dialog should be opened anyway),
   * the default name selector is then returned, which is to just take the file name as snippet file name.
   */
  private fun getFileNameExtractor(files: List<VirtualFile>,
                                   pathHandlingMode: PathHandlingMode): suspend (VirtualFile) -> String =
    if (files.isEmpty()) {
      pathFromName
    }
    else {
      when (pathHandlingMode) {
        PathHandlingMode.RelativePaths -> pathFromNearestCommonAncestor(files)
        PathHandlingMode.ProjectRelativePaths -> pathFromProjectRoot()
        PathHandlingMode.ContentRootRelativePaths -> pathFromContentRoot()
        PathHandlingMode.FlattenedPaths -> pathFromName
      }
    }

  /**
   * Checks whether the user should be able to see and click the 'Create Snippet' button.
   */
  fun canOpenDialog(editor: Editor?, selectedFile: VirtualFile?, selectedFiles: List<VirtualFile>?): Boolean {
    if (project.isDefault || service<GitLabAccountManager>().accountsState.value.isEmpty()) {
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
  private suspend fun showDialog(cs: CoroutineScope, editor: Editor?, files: List<VirtualFile>): GitLabCreateSnippetViewModel? {
    val vm = createVM(cs, editor, files)

    val dialogIsOk = cs.async(Dispatchers.Main) {
      val dialog = GitLabCreateSnippetComponentFactory
        .create(cs, project, vm)

      dialog.showAndGet()
    }

    // Await result of dialog
    if (!dialogIsOk.await()) {
      return null
    }

    return vm
  }

  /**
   * Creates the view-model object for representing the creation of a snippet.
   */
  private suspend fun createVM(cs: CoroutineScope, editor: Editor?, files: List<VirtualFile>): GitLabCreateSnippetViewModel {
    val availablePathHandlingModes = PathHandlingMode.values().filter {
      val extractor = getFileNameExtractor(files, it)
      files.map { extractor(it) }.toSet().size == files.size // Check that there are no duplicates when mapped
    }

    return GitLabCreateSnippetViewModel(
      cs,
      project,
      service<GitLabAccountManager>(),
      service<GitLabApiManager>(),
      editor,
      files,
      availablePathHandlingModes.toSet(),
      GitLabCreateSnippetViewModelData(
        "",
        "",

        true,
        true,
        false,

        null,
        PathHandlingMode.RelativePaths
      )
    )
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
    if (isFile && fileType.isBinary || collection.size > GL_SNIPPET_FILES_LIMIT || isRecursiveOrCircularSymlink) {
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
   * Gets the name of a snippet entry from the relative path from the content root of a file.
   */
  private fun pathFromContentRoot(): suspend (VirtualFile) -> String {
    val pfi = project.service<ProjectFileIndex>()
    return { file ->
      readAction { pfi.getContentRootForFile(file) }
        ?.let { root -> VfsUtilCore.getRelativePath(file, root) } ?: file.name
    }
  }

  /**
   * Gets the name of a snippet entry from the relative path from the project root.
   */
  private fun pathFromProjectRoot(): suspend (VirtualFile) -> String {
    val projectRoot = project.guessProjectDir() ?: return { file -> file.name }
    return { file ->
      VfsUtilCore.getRelativePath(file, projectRoot) ?: file.name
    }
  }

  /**
   * Gets the name of a snippet entry from the nearest common ancestor of all files (could be the file system root directory).
   *
   * @param files The collection of files from which snippet content is gathered. May never be empty.
   */
  private fun pathFromNearestCommonAncestor(files: Collection<VirtualFile>): suspend (VirtualFile) -> String {
    var closestRoot = files.first().parent
    for (file in files.drop(1)) {
      closestRoot = VfsUtilCore.getCommonAncestor(closestRoot, file)
    }
    return { file ->
      VfsUtilCore.getRelativePath(file, closestRoot) ?: file.name
    }
  }
}