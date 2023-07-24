// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
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
  private val pathFromName: (VirtualFile) -> String = { file -> file.name }

  /**
   * Performs the ['Create Snippet' action][GitLabCreateSnippetAction] by showing the 'Create Snippet' dialog
   * and performing the GitLab API request to create the final snippet.
   */
  fun performCreateSnippetAction(e: AnActionEvent) {
    serviceScope.launch {
      val cs = serviceScope

      val editor = e.getData(CommonDataKeys.EDITOR)
      val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
      val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()

      val files = collectNonBinaryFiles(listOfNotNull(editor?.virtualFile).ifEmpty { null }
                                        ?: selectedFiles
                                        ?: listOfNotNull(selectedFile))
      val vm = createVM(cs, e, files)

      // Await result of dialog
      if (!cs.async(Dispatchers.Main) {
          val dialog = GitLabCreateSnippetComponentFactory
            .create(cs, project, vm)

          dialog.showAndGet()
        }.await()) {
        return@launch
      }

      withBackgroundProgress(project, message("snippet.create.action.progress"), true) {
        try {
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
              glContents.file?.let(fileNameExtractor) ?: "",
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
          if (httpResult.statusCode() != 200) {
            throw IllegalStateException(message("snippet.create.action.error.http-status", httpResult.statusCode()))
          }
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
        catch (e: Exception) {
          VcsNotifier.getInstance(project)
            .notifyError(GL_NOTIFICATION_CREATE_SNIPPET_ERROR, message("snippet.create.action.error.title"), e.localizedMessage, true)
        }
      }
    }
  }

  /**
   * Gets the file name extractor function for the given [PathHandlingMode] using the given set of [files][VirtualFile].
   * If the list of files are empty, the name selector used should not matter (and no dialog should be opened anyway),
   * the default name selector is then returned, which is to just take the file name as snippet file name.
   */
  private fun getFileNameExtractor(files: List<VirtualFile>,
                                   pathHandlingMode: PathHandlingMode): (VirtualFile) -> String =
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
  fun canOpenDialog(e: AnActionEvent): Boolean {
    if (project.isDefault || service<GitLabAccountManager>().accountsState.value.isEmpty()) {
      return false
    }

    val editor = e.getData(CommonDataKeys.EDITOR)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val files = collectNonBinaryFiles(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
                                      ?: listOfNotNull(file))

    if (editor != null) {
      return editor.document.textLength != 0
    }

    return files.size in 1..GL_SNIPPET_FILES_LIMIT
  }

  /**
   * Creates the view-model object for representing the creation of a snippet.
   */
  private fun createVM(cs: CoroutineScope, e: AnActionEvent, files: List<VirtualFile>): GitLabCreateSnippetViewModel {
    val contents = cs.async(Dispatchers.IO) { collectContents(e) }

    val availablePathHandlingModes = PathHandlingMode.values().filter {
      val extractor = getFileNameExtractor(files, it)
      files.map(extractor).toSet().size == files.size // Check that there are no duplicates when mapped
    }

    return GitLabCreateSnippetViewModel(
      cs,
      project,
      contents,
      availablePathHandlingModes.toSet(),
      CreateSnippetViewModelData(
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
  private fun pathFromContentRoot(): (VirtualFile) -> String {
    val pfi = project.service<ProjectFileIndex>()
    return { file ->
      ReadAction.computeCancellable<VirtualFile?, Nothing> { pfi.getContentRootForFile(file) }
        ?.let { root -> VfsUtilCore.getRelativePath(file, root) } ?: file.name
    }
  }

  /**
   * Gets the name of a snippet entry from the relative path from the project root.
   */
  private fun pathFromProjectRoot(): (VirtualFile) -> String {
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
  private fun pathFromNearestCommonAncestor(files: Collection<VirtualFile>): (VirtualFile) -> String {
    var closestRoot = files.first().parent
    for (file in files.drop(1)) {
      closestRoot = VfsUtilCore.getCommonAncestor(closestRoot, file)
    }
    return { file ->
      VfsUtilCore.getRelativePath(file, closestRoot) ?: file.name
    }
  }

  /**
   * Collects the contents that are supposed to be part of the snippet from the given action event.
   *
   * @return All file contents that are selected and not empty.
   */
  private fun collectContents(e: AnActionEvent): List<GitLabSnippetFileContents> {
    val editor = e.getData(CommonDataKeys.EDITOR)                     // If editor in focus
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)  // If multiple files are selected
    val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)         // If in editor or on file (or on tab in file)

    return ReadAction.computeCancellable<List<GitLabSnippetFileContents>, Nothing> {
      collectContents(editor, selectedFiles, selectedFile)
        ?.filter { it.capturedContents.isNotEmpty() }
      ?: listOf()
    }
  }

  /**
   * Collects the contents from [Editor], or list of [files][VirtualFile], or [file][VirtualFile] in that order.
   * The first content holder that is not `null` will be used.
   */
  private fun collectContents(editor: Editor?,
                              files: Array<VirtualFile>?,
                              file: VirtualFile?): List<GitLabSnippetFileContents>? =
    if (editor != null) {
      editor.collectContents()?.let(::listOf)
    }
    else {
      collectNonBinaryFiles(files?.toList() ?: listOfNotNull(file)).map { f ->
        f.collectContents() ?: GitLabSnippetFileContents(f, "")
      }
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
    val content = selectionModel.getSelectedText(true)?.ifEmpty { null }
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