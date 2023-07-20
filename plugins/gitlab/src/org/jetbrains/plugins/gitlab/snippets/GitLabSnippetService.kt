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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import java.awt.datatransfer.StringSelection

/**
 * Service required to get a [CoroutineScope] when performing [GitLabCreateSnippetAction].
 */
@Service(Service.Level.PROJECT)
class GitLabSnippetService(private val project: Project, private val serviceScope: CoroutineScope) {
  /**
   * Gets the name of a snippet entry directly from the file name.
   */
  private val pathFromName: (VirtualFile) -> String = { file -> file.name }

  /**
   * Performs the ['Create Snippet' action][GitLabCreateSnippetAction] by showing the 'Create Snippet' dialog
   * and performing the GitLab API request to create the final snippet.
   */
  fun performCreateSnippetAction(e: AnActionEvent) {
    serviceScope.launch(Dispatchers.Default) {
      val cs = this
      val vm = createVM(cs, e) ?: return@launch   // TODO: Display error (and make button unavailable)

      // Await result of dialog
      if (!cs.async(Dispatchers.Main) {
          val dialog = GitLabCreateSnippetComponentFactory
            .create(cs, project, vm)

          dialog.showAndGet()
        }.await()) {
        return@launch
      }

      // Process result by creating the snippet, copying url, etc.
      val data = vm.data
      val api = vm.getApi() ?: return@launch       // TODO: Display error
      val server = vm.getServer() ?: return@launch // TODO: Display error

      val contents = vm.contents.await()
      val files = contents.mapNotNull { it.file }

      val fileNameExtractor = if (files.isEmpty()) {
        pathFromName
      }
      else {
        when (data.pathHandlingMode) {
          PathHandlingMode.RelativePaths -> pathFromNearestCommonAncestor(files)
          PathHandlingMode.ProjectRelativePaths -> pathFromProjectRoot()
          PathHandlingMode.ContentRootRelativePaths -> pathFromContentRoot()
          PathHandlingMode.FlattenedPaths -> pathFromName
        }
      }

      val result = api.graphQL.createSnippet(
        server,
        data.onProject,
        data.title,
        data.description,
        if (data.isPrivate) GitLabVisibilityLevel.private else GitLabVisibilityLevel.public,
        contents,
        fileNameExtractor
      ).body() ?: return@launch                   // TODO: Display error

      val snippet = result.value ?: return@launch // TODO: Display error
      val url = snippet.webUrl

      if (data.isCopyUrl) {
        CopyPasteManager.getInstance().setContents(StringSelection(url))
      }
      if (data.isOpenInBrowser) {
        BrowserUtil.browse(url)
      }
    }
  }

  /**
   * Creates the view-model object for representing the creation of a snippet.
   */
  private fun createVM(cs: CoroutineScope, e: AnActionEvent): GitLabCreateSnippetViewModel {
    val editor = e.getData(CommonDataKeys.EDITOR)                     // If editor in focus
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)  // If multiple files are selected
    val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)         // If in editor or on file (or on tab in file)

    val contents = cs.async(Dispatchers.IO) {
      ReadAction.computeCancellable<List<GitLabSnippetFileContents>?, Nothing> {
        collectContents(editor, selectedFiles, selectedFile) ?: listOf()
      }
    }

    return GitLabCreateSnippetViewModel(
      cs,
      project,
      contents,
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
   * Collects the contents from [Editor], or list of [files][VirtualFile], or [file][VirtualFile] in that order.
   * The first content holder that is not `null` will be used.
   */
  private fun collectContents(editor: Editor?,
                              files: Array<VirtualFile>?,
                              file: VirtualFile?): List<GitLabSnippetFileContents>? =
    if (editor != null) {
      editor.collectContents()?.let(::listOf)
    }
    else if (files != null) {
      files.map { f ->
        f.collectContents() ?: GitLabSnippetFileContents(f, "")
      }
    }
    else {
      file?.collectContents()?.let(::listOf)
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