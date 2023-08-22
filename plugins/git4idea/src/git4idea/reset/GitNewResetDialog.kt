/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.reset

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class GitNewResetDialog(private val project: Project,
                        private val commits: Map<GitRepository, VcsFullCommitDetails>,
                        defaultMode: GitResetMode) : DialogWrapper(project) {

  var resetMode: GitResetMode = defaultMode
    private set

  init {
    init()
    title = GitBundle.message("git.reset.dialog.title")
    setOKButtonText(GitBundle.message("git.reset.button"))
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(XmlStringUtil.wrapInHtml(prepareDescription(project, commits)))
      }
      row {
        label(XmlStringUtil.wrapInHtml(GitBundle.message("git.reset.dialog.description")))
      }
      buttonsGroup {
        for (mode in GitResetMode.values()) {
          val name = mode.getName()
          row {
            radioButton(name, mode)
              .applyToComponent { mnemonic = name[0].code }
              .comment(mode.description)
          }
        }
      }.bind(::resetMode)
    }
  }

  override fun getHelpId() = DIALOG_ID

  companion object {
    private const val DIALOG_ID = "git.new.reset.dialog" //NON-NLS

    private fun prepareDescription(project: Project, commits: Map<GitRepository, VcsFullCommitDetails>): @Nls String {
      val isMultiRepo = GitRepositoryManager.getInstance(project).moreThanOneRoot()
      val onlyCommit = commits.entries.singleOrNull()
      if (onlyCommit != null && !isMultiRepo) {
        val (key, value) = onlyCommit
        return "${getSourceText(key)} -> ${getTargetText(value)}" //NON-NLS
      }

      val desc: @Nls StringBuilder = StringBuilder()
      for ((repository, commit) in commits) {
        val sourceInRepo = GitBundle.message("git.reset.dialog.description.source.in.repository",
                                             getSourceText(repository),
                                             DvcsUtil.getShortRepositoryName(repository))
        desc.append("${sourceInRepo} -> ${getTargetText(commit)}<br/>") //NON-NLS
      }
      return desc.toString() //NON-NLS
    }

    private fun getTargetText(commit: VcsFullCommitDetails): @Nls String {
      val commitMessage = StringUtil.shortenTextWithEllipsis(commit.subject, 40, 0)
      val author = StringUtil.shortenTextWithEllipsis(VcsUserUtil.getShortPresentation(commit.author), 40, 0)
      return GitBundle.message("git.reset.dialog.description.commit.details.by.author",
                               HtmlBuilder()
                                 .append(HtmlChunk.text(commit.id.toShortString()).bold())
                                 .append(HtmlChunk.text(" \"$commitMessage\""))
                                 .toString(),
                               HtmlChunk.tag("code").addText(author))
    }

    private fun getSourceText(repository: GitRepository): @NonNls String {
      val currentBranch = repository.currentBranch
      val currentRevision = repository.currentRevision
      val text = when {
        currentBranch != null -> currentBranch.name
        currentRevision != null -> "${GitUtil.HEAD} (${DvcsUtil.getShortHash(currentRevision)})" //NON-NLS
        else -> GitUtil.HEAD //NON-NLS
      }
      return HtmlChunk.text(text).bold().toString()
    }
  }
}