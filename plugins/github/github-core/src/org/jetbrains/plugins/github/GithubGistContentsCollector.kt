// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest

interface GithubGistContentsCollector {
  data class GistEventData(
    val project: Project,
    val editor: Editor?,
    val file: VirtualFile?,
    val files: Array<VirtualFile>?,
  )

  /**
   * Collects the contents of a GitHub Gist based on the provided event data.
   *
   * @param gistEventData Data related to the Gist event, including project reference, editor instance, and target files.
   * @return A list of file contents for the Gist request.
   * Null indicates that this collector isn't responsible for this type of data, and the content should be passed to the next collector.
   * Empty list means that the collector is aware of this type of data but wasn't able to collect any content.
   */
  fun collectContents(gistEventData: GistEventData): List<GithubGistRequest.FileContent>?

  /**
   * Retrieves the displayed file name for a gist from the given `editor` and `files` array.
   *
   * @param editor The editor object which might be null.
   * @param files An array of VirtualFile objects which might be null.
   * @return The file name as a String if found, otherwise returns null.
   */
  fun getGistFileName(editor: Editor?, files: Array<VirtualFile>?): String?

  companion object {
    val EP = ExtensionPointName.create<GithubGistContentsCollector>("com.intellij.vcs.github.gistContentsCollector")

    fun collectContents(
      project: Project,
      editor: Editor?,
      file: VirtualFile?,
      files: Array<VirtualFile>?,
    ): List<GithubGistRequest.FileContent> {
      val eventData = GistEventData(project, editor, file, files)
      return EP.extensionList.firstNotNullOf {
        it.collectContents(eventData)
      }
    }

    fun getGistFileName(editor: Editor?, files: Array<VirtualFile>?): String? {
      return EP.extensionList.firstNotNullOfOrNull {
        it.getGistFileName(editor, files)
      }
    }
  }
}
