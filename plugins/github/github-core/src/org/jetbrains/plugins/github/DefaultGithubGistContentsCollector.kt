// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil
import java.io.IOException

open class DefaultGithubGistContentsCollector : GithubGistContentsCollector {
  override fun getGistFileName(editor: Editor?, files: Array<VirtualFile>?): String? {
    val onlyFile = files?.singleOrNull()?.takeIf { !it.isDirectory }
    if (onlyFile != null) {
      return onlyFile.name
    }
    if (editor != null) {
      return ""
    }
    return null
  }

  override fun collectContents(gistEventData: GithubGistContentsCollector.GistEventData): List<GithubGistRequest.FileContent> {
    val (project, editor, file, files) = gistEventData

    if (editor != null) {
      val contents = getContentFromEditor(editor, file)
      if (contents != null) {
        return contents
      }
    }

    val myFiles: Array<VirtualFile>? = files ?: file?.let { arrayOf(it) }
    if (myFiles != null) {
      return buildList {
        for (vf in myFiles) {
          addAll(getContentFromFile(vf, project, null))
        }
      }
    }

    LOG.error("File, files and editor can't be null all at once!")
    throw IllegalStateException("File, files and editor can't be null all at once!")
  }

  protected open fun getContentFromEditor(
    editor: Editor,
    file: VirtualFile?,
  ): List<GithubGistRequest.FileContent>? {
    val text: String = getSelectedText(editor) ?: editor.document.text
    if (text.isBlank()) {
      return null
    }

    val fileName = file?.name.orEmpty()
    return listOf(GithubGistRequest.FileContent(fileName, text))
  }

  protected fun getSelectedText(editor: Editor): String? =
    ReadAction.compute<String?, java.lang.RuntimeException> { editor.selectionModel.selectedText }

  private fun getContentFromFile(file: VirtualFile, project: Project, prefix: String?): List<GithubGistRequest.FileContent> {
    if (file.isDirectory) {
      return getContentFromDirectory(file, project, prefix)
    }
    if (file.fileType.isBinary) {
      GithubNotifications.showWarning(project, GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                      GithubBundle.message("cannot.create.gist"),
                                      GithubBundle.message("create.gist.error.binary.file", file.name))
      return emptyList()
    }
    val content = WriteAction.computeAndWait<String?, RuntimeException> { getFileContents(file) }
    if (content == null) {
      GithubNotifications.showWarning(project,
                                      GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                      GithubBundle.message("cannot.create.gist"),
                                      GithubBundle.message("create.gist.error.content.read", file.name))
      return emptyList()
    }
    if (content.isBlank()) {
      return emptyList()
    }
    val filename = addPrefix(file.name, prefix, false)
    return listOf(GithubGistRequest.FileContent(filename, content))
  }

  private fun getFileContents(file: VirtualFile): @NlsSafe String? {
    try {
      return getFileContentInternal(file)
    }
    catch (e: IOException) {
      LOG.info("Couldn't read contents of the file $file", e)
      return null
    }
  }

  protected open fun getFileContentInternal(file: VirtualFile): @NlsSafe String? {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = fileDocumentManager.getDocument(file)
    if (document != null) {
      fileDocumentManager.saveDocument(document)
      return document.text
    }
    else {
      return String(file.contentsToByteArray(), file.charset)
    }
  }

  private fun getContentFromDirectory(dir: VirtualFile, project: Project, prefix: String?): List<GithubGistRequest.FileContent> {
    val contents: MutableList<GithubGistRequest.FileContent> = ArrayList()
    for (file in dir.children) {
      if (!isFileIgnored(file, project)) {
        val pref = addPrefix(dir.name, prefix, true)
        contents.addAll(getContentFromFile(file, project, pref))
      }
    }
    return contents
  }

  private fun addPrefix(name: String, prefix: String?, addTrailingSlash: Boolean): String {
    var pref = prefix ?: ""
    pref += name
    if (addTrailingSlash) {
      pref += "_"
    }
    return pref
  }

  private fun isFileIgnored(file: VirtualFile, project: Project): Boolean {
    val manager = ChangeListManager.getInstance(project)
    return manager.isIgnoredFile(file) || FileTypeManager.getInstance().isFileIgnored(file)
  }

  companion object {
    private val LOG = GithubUtil.LOG
  }
}