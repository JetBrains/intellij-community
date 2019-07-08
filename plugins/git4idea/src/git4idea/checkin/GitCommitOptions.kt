// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListData
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.EditorTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor.StringValueDescriptor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.AmendCommitHandler
import com.intellij.vcs.commit.AmendCommitModeListener
import com.intellij.vcs.commit.ToggleAmendCommitOption
import com.intellij.vcs.commit.commitProperty
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.util.VcsUserUtil.isSamePerson
import git4idea.GitUserRegistry
import git4idea.GitUtil.getRepositoryManager
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent.VK_G
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

private val COMMIT_AUTHOR_KEY = Key.create<String>("Git.Commit.Author")
private val COMMIT_AUTHOR_DATE_KEY = Key.create<Date>("Git.Commit.AuthorDate")
private val IS_SIGN_OFF_COMMIT_KEY = Key.create<Boolean>("Git.Commit.IsSignOff")
private val IS_COMMIT_RENAMES_SEPARATELY_KEY = Key.create<Boolean>("Git.Commit.IsCommitRenamesSeparately")

internal var CommitContext.commitAuthor: String? by commitProperty(COMMIT_AUTHOR_KEY, null)
internal var CommitContext.commitAuthorDate: Date? by commitProperty(COMMIT_AUTHOR_DATE_KEY, null)
internal var CommitContext.isSignOffCommit: Boolean by commitProperty(IS_SIGN_OFF_COMMIT_KEY)
internal var CommitContext.isCommitRenamesSeparately: Boolean by commitProperty(IS_COMMIT_RENAMES_SEPARATELY_KEY)

private fun createTextField(project: Project, values: List<String>): EditorTextField {
  val completionProvider = ValuesCompletionProviderDumbAware(StringValueDescriptor(), values)

  return object : TextFieldWithCompletion(project, completionProvider, "", true, true, true) {
    override fun updateUI() {
      // When switching from Darcula to IntelliJ `getBackground()` has `UIUtil.getTextFieldBackground()` value which is `UIResource`.
      // `LookAndFeel.installColors()` (called from `updateUI()`) calls `setBackground()` and sets panel background (gray) to be used.
      // So we clear background to allow default behavior (use background from color scheme).
      background = null
      super.updateUI()
    }
  }
}

private fun getAllUsers(project: Project): List<String> =
  project.service<VcsUserRegistry>().users.map { VcsUserUtil.toExactString(it) }

private val HierarchyEvent.isShowingChanged get() = (changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L

class GitCommitOptionsUi(
  private val commitPanel: CheckinProjectPanel,
  private val commitContext: CommitContext,
  private val explicitMovementProviders: List<GitCheckinExplicitMovementProvider>,
  private val showAmendOption: Boolean
) : RefreshableOnComponent,
    CheckinChangeListSpecificComponent,
    AmendCommitModeListener,
    Disposable {

  private val project get() = commitPanel.project
  private val settings = GitVcsSettings.getInstance(project)
  private val userRegistry = GitUserRegistry.getInstance(project)
  val amendHandler: AmendCommitHandler get() = commitPanel.commitWorkflowHandler.amendCommitHandler

  private var authorDate: Date? = null

  private val panel = JPanel(GridBagLayout())
  private val authorField = createTextField(project, getKnownCommitAuthors())
  private val signOffCommit = JBCheckBox("Sign-off commit", settings.shouldSignOffCommit()).apply {
    mnemonic = VK_G

    val user = commitPanel.roots.mapNotNull { userRegistry.getUser(it) }.firstOrNull()
    val signature = user?.let { escapeXmlEntities(VcsUserUtil.toExactString(it)) }.orEmpty()
    toolTipText = "<html>Adds the following line at the end of the commit message:<br/>" +
                  "Signed-off by: $signature</html>"
  }
  private val commitRenamesSeparately = JBCheckBox(
    explicitMovementProviders.singleOrNull()?.description ?: "Create extra commit with file movements",
    settings.isCommitRenamesSeparately
  )

  private var authorWarning: Balloon? = null

  init {
    authorField.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) = clearAuthorWarning()
    })
    authorField.addHierarchyListener(object : HierarchyListener {
      override fun hierarchyChanged(e: HierarchyEvent) {
        if (e.isShowingChanged && authorField.isShowing && authorField.text.isNotBlank()) {
          showAuthorWarning()
          authorField.removeHierarchyListener(this)
        }
      }
    })

    buildLayout()

    amendHandler.addAmendCommitModeListener(this, this)
  }

  private fun buildLayout() = panel.apply {
    val gb = GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(JBUI.insets(2))

    val authorLabel = JBLabel(GitBundle.message("commit.author")).apply { labelFor = authorField }
    add(authorLabel, gb.nextLine().next())
    add(authorField, gb.next().fillCellHorizontally().weightx(1.0))

    val amendOption = if (showAmendOption) ToggleAmendCommitOption(commitPanel, this@GitCommitOptionsUi) else null
    amendOption?.let { add(it, gb.nextLine().next().coverLine()) }

    add(signOffCommit, gb.nextLine().next().coverLine())
    add(commitRenamesSeparately, gb.nextLine().next().coverLine())
  }

  override fun amendCommitModeToggled() = updateRenamesCheckboxState()

  override fun dispose() = Unit

  override fun getComponent(): JComponent = panel

  override fun restoreState() = refresh()

  override fun refresh() {
    updateRenamesCheckboxState()
    authorField.setText(null)
    clearAuthorWarning()
    authorDate = null
  }

  override fun saveState() {
    val author = getAuthor()

    commitContext.apply {
      commitAuthor = author
      commitAuthorDate = authorDate
      isSignOffCommit = signOffCommit.isSelected
      isCommitRenamesSeparately = commitRenamesSeparately.run { isEnabled && isSelected }
    }

    settings.apply {
      author?.let { saveCommitAuthor(it) }
      setSignOffCommit(signOffCommit.isSelected)
      isCommitRenamesSeparately = commitRenamesSeparately.isSelected
    }
  }

  override fun onChangeListSelected(list: LocalChangeList) {
    updateRenamesCheckboxState()
    clearAuthorWarning()

    val data = list.data as? ChangeListData
    setAuthor(data?.author)
    authorDate = data?.date

    panel.revalidate()
    panel.repaint()
  }

  fun getAuthor(): String? = authorField.text.takeIf { it.isNotBlank() }?.let { GitCommitAuthorCorrector.correct(it) }

  private fun setAuthor(author: VcsUser?) {
    if (author != null && !isDefaultAuthor(author)) {
      authorField.text = VcsUserUtil.toExactString(author)
      authorField.putClientProperty("JComponent.outline", "warning")
      if (authorField.isShowing) {
        showAuthorWarning()
      }
    }
    else {
      authorField.setText(null)
    }
  }

  private fun updateRenamesCheckboxState() {
    val canCommitRenamesSeparately = explicitMovementProviders.isNotEmpty() && Registry.`is`("git.allow.explicit.commit.renames")

    commitRenamesSeparately.isVisible = canCommitRenamesSeparately
    commitRenamesSeparately.isEnabled = canCommitRenamesSeparately && !amendHandler.isAmendCommitMode
  }

  private fun showAuthorWarning() {
    if (authorWarning?.isDisposed == false) return

    val builder = JBPopupFactory.getInstance()
      .createBalloonBuilder(JLabel(GitBundle.getString("commit.author.diffs")))
      .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets"))
      .setBorderColor(JBUI.CurrentTheme.Validator.warningBorderColor())
      .setFillColor(JBUI.CurrentTheme.Validator.warningBackgroundColor())
      .setHideOnClickOutside(true)
      .setHideOnFrameResize(false)

    authorWarning = builder.createBalloon()
    authorWarning?.show(RelativePoint(authorField, Point(authorField.width / 2, authorField.height)), Balloon.Position.below)
  }

  private fun clearAuthorWarning() {
    authorField.putClientProperty("JComponent.outline", null)
    authorWarning?.hide()
    authorWarning = null
  }

  private fun getKnownCommitAuthors(): List<String> = (getAllUsers(project) + settings.commitAuthors).distinct().sorted()

  private fun isDefaultAuthor(author: VcsUser): Boolean {
    val repositoryManager = getRepositoryManager(project)
    val affectedRoots = commitPanel.roots.filter { repositoryManager.getRepositoryForRoot(it) != null }

    return affectedRoots.map { userRegistry.getUser(it) }.all { it != null && isSamePerson(author, it) }
  }
}