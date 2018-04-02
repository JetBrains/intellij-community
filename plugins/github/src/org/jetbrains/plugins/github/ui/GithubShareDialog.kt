package org.jetbrains.plugins.github.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.util.regex.Pattern
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubShareDialog(project: Project,
                        private val existingRepos: Set<String>,
                        private val existingRemotes: Set<String>,
                        privateRepoAllowed: Boolean) : DialogWrapper(project) {
  private val GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+")

  private val repositoryTextField = JBTextField(project.name)
  private val privateCheckBox = JBCheckBox("Private", privateRepoAllowed).apply {
    toolTipText = "Your account doesn't support private repositories"
  }
  private val remoteTextField = JBTextField(if (existingRemotes.isEmpty()) "origin" else "github")
  private val descriptionTextArea = JTextArea()

  init {
    title = "Share Project On GitHub"
    setOKButtonText("Share")
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val descriptionPane = JBScrollPane(descriptionTextArea)
    descriptionPane.minimumSize = JBDimension(150, 50)
    descriptionPane.preferredSize = JBDimension(150, 50)
    descriptionPane.border = BorderFactory.createEtchedBorder()

    return grid()
      .add(panel(simplePanel(UIUtil.DEFAULT_HGAP, 0).addToCenter(repositoryTextField).addToRight(privateCheckBox))
             .withLabel("Repository name:"))
      .add(panel(remoteTextField).withLabel("Remote:"))
      .add(panel(descriptionPane).withLabel("Description:"))
      .createPanel()
  }

  override fun doValidateAll(): List<ValidationInfo> {
    val repositoryName = repositoryTextField.text
    return listOf(
      {
        if (repositoryName.isNullOrBlank()) ValidationInfo("No repository name selected",
                                                           repositoryTextField)
        else null
      },
      {
        if (existingRepos.contains(repositoryName)) ValidationInfo("Repository with selected name already exists",
                                                                   repositoryTextField)
        else null
      },
      {
        if (existingRemotes.contains(getRemoteName())) ValidationInfo("Remote with selected name already exists",
                                                                      remoteTextField)
        else null
      },
      {
        if (!GITHUB_REPO_PATTERN.matcher(repositoryName).matches()) ValidationInfo(
          "Invalid repository name. Name should consist of letters, numbers, dashes, dots and underscores",
          repositoryTextField)
        else null
      }

    ).mapNotNull { it() }
  }

  override fun getHelpId() = "github.share"
  override fun getDimensionServiceKey() = "Github.ShareDialog"
  override fun getPreferredFocusedComponent() = repositoryTextField

  fun getRepositoryName(): String = repositoryTextField.text
  fun getRemoteName(): String = remoteTextField.text
  fun isPrivate(): Boolean = privateCheckBox.isSelected
  fun getDescription(): String = descriptionTextArea.text

  @TestOnly
  fun testSetRepositoryName(name: String) {
    repositoryTextField.text = name
  }
}
