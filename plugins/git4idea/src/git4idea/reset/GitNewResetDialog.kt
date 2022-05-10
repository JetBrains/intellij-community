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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.RadioButtonEnumModel
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class GitNewResetDialog(private val myProject: Project,
                        private val myCommits: Map<GitRepository, VcsFullCommitDetails>,
                        private val myDefaultMode: GitResetMode) : DialogWrapper(myProject) {
  private val myButtonGroup: ButtonGroup = ButtonGroup()
  private val myEnumModel: RadioButtonEnumModel<GitResetMode> = RadioButtonEnumModel.bindEnum(GitResetMode::class.java, myButtonGroup)

  init {
    init()
    title = GitBundle.message("git.reset.dialog.title")
    setOKButtonText(GitBundle.message("git.reset.button"))
    isResizable = false
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(GridBagLayout())
    val gb = GridBag().setDefaultAnchor(GridBagConstraints.LINE_START).setDefaultInsets(0, UIUtil.DEFAULT_HGAP, UIUtil.LARGE_VGAP, 0)
    val description = prepareDescription(myProject, myCommits)
    panel.add(JBLabel(XmlStringUtil.wrapInHtml(description)), gb.nextLine().next().coverLine())
    val descriptionLabel = JBLabel(XmlStringUtil.wrapInHtml(GitBundle.message("git.reset.dialog.description")), UIUtil.ComponentStyle.SMALL)
    panel.add(descriptionLabel, gb.nextLine().next().coverLine())
    for (mode in GitResetMode.values()) {
      val button = JBRadioButton(mode.getName())
      button.setMnemonic(mode.getName()[0])
      myButtonGroup.add(button)
      panel.add(button, gb.nextLine().next())
      panel.add(JBLabel(XmlStringUtil.wrapInHtml(mode.description), UIUtil.ComponentStyle.SMALL), gb.next())
    }
    myEnumModel.selected = myDefaultMode
    return panel
  }

  override fun getHelpId(): String {
    return DIALOG_ID
  }

  val resetMode: GitResetMode
    get() = myEnumModel.selected

  companion object {
    private const val DIALOG_ID = "git.new.reset.dialog" //NON-NLS

    private fun prepareDescription(project: Project, commits: Map<GitRepository, VcsFullCommitDetails>): @Nls String {
      if (commits.size == 1 && !isMultiRepo(project)) {
        val (key, value) = commits.entries.iterator().next()
        return String.format("%s -> %s", getSourceText(key), getTargetText(value)) //NON-NLS
      }
      val desc: @NlsSafe StringBuilder = StringBuilder()
      for ((repository, commit) in commits) {
        val sourceInRepo = GitBundle.message("git.reset.dialog.description.source.in.repository",
                                             getSourceText(repository),
                                             DvcsUtil.getShortRepositoryName(repository))
        desc.append(String.format("%s -> %s<br/>", //NON-NLS
                                  sourceInRepo,
                                  getTargetText(commit)))
      }
      return desc.toString()
    }

    private fun getTargetText(commit: VcsFullCommitDetails): @Nls String {
      val commitMessage = StringUtil.shortenTextWithEllipsis(commit.subject, 20, 0)
      val commitDetails: HtmlChunk = HtmlChunk.tag("code").children(
        HtmlChunk.text(commit.id.toShortString()).bold(),
        HtmlChunk.text(" \"$commitMessage\""))
      val author: HtmlChunk = HtmlChunk.tag("code").addText(VcsUserUtil.getShortPresentation(commit.author))
      return GitBundle.message("git.reset.dialog.description.commit.details.by.author", commitDetails, author)
    }

    private fun getSourceText(repository: GitRepository): @NonNls String {
      val currentRevision = repository.currentRevision!!
      val text = when (repository.currentBranch) {
        null -> "HEAD (" + DvcsUtil.getShortHash(currentRevision) + ")" //NON-NLS
        else -> repository.currentBranch!!.name
      }
      return XmlStringUtil.wrapInHtmlTag(text, "b")
    }

    private fun isMultiRepo(project: Project): Boolean {
      return GitRepositoryManager.getInstance(project).moreThanOneRoot()
    }
  }
}