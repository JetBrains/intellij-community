// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history.recap

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.project.stateStore
import com.intellij.util.ui.UIUtil.LINE_SEPARATOR
import java.io.StringWriter

internal object RecapPatchesUtils {

  //FIXME LLMVcsPatchBuilder: if the privacy is mandatory, consider using LLMVcsPatchBuilder directly!
  fun writePatches(
    project: Project,
    vcsChanges: Collection<Change>,
    writer: StringWriter,
  ) {
    IdeaTextPatchBuilder
      .buildPatch(project, vcsChanges, project.stateStore.projectBasePath, false, true)
      .apply { writePatches(writer, this, true) }
  }

  private fun writePatches(writer: StringWriter, patches: List<FilePatch>, shortenDeletedFiles: Boolean) {
    patches.forEach { patch ->
      if (patch is TextFilePatch && patch.isDeletedFile && shortenDeletedFiles) {
        writer.write("""
            deleted file %s
        """.format(LocalFilePath(patch.beforeName, false).path).trimIndent() + "\n")
      }
      else {
        UnifiedDiffWriter.writeBeforePath(writer, patch, LINE_SEPARATOR, false)
        UnifiedDiffWriter.writeAfterPath(writer, patch, LINE_SEPARATOR, false)
        if (patch is TextFilePatch) {
          UnifiedDiffWriter.writeHunk(writer, patch, LINE_SEPARATOR, LINE_SEPARATOR)
        }
      }
    }
  }
}
