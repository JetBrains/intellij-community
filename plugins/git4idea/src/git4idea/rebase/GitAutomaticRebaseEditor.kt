/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.rebase

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.config.GitConfigUtil
import git4idea.rebase.GitRebaseEditorMain.ERROR_EXIT_CODE
import java.io.File

internal class GitAutomaticRebaseEditor(private val project: Project,
                                        private val root: VirtualFile,
                                        private val entriesEditor: (List<GitRebaseEntry>) -> List<GitRebaseEntry>,
                                        private val plainTextEditor: (String) -> String
) : GitInteractiveRebaseEditorHandler(GitRebaseEditorService.getInstance(), project, root) {
  val LOG = logger<GitAutomaticRebaseEditor>()

  override fun editCommits(path: String): Int {
    try {
      if (!myRebaseEditorShown) {
        myRebaseEditorShown = true

        val file = GitInteractiveRebaseFile(project, root, path)
        val entries = file.load()
        file.save(entriesEditor(entries))
      }
      else {
        val encoding = GitConfigUtil.getCommitEncoding(project, root)
        val originalMessage = FileUtil.loadFile(File(path), encoding)
        val modifiedMessage = plainTextEditor(originalMessage)
        FileUtil.writeToFile(File(path), modifiedMessage.toByteArray(charset(encoding)))
      }
      return 0
    }
    catch (ex: Exception) {
      LOG.error("Editor failed: ", ex)
      return ERROR_EXIT_CODE
    }
  }
}