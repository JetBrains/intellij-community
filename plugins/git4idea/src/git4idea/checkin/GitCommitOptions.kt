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
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.*
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserEditor
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

private val COMMIT_AUTHOR_KEY = Key.create<VcsUser>("Git.Commit.Author")
private val COMMIT_AUTHOR_DATE_KEY = Key.create<Date>("Git.Commit.AuthorDate")
private val IS_SIGN_OFF_COMMIT_KEY = Key.create<Boolean>("Git.Commit.IsSignOff")
private val IS_COMMIT_RENAMES_SEPARATELY_KEY = Key.create<Boolean>("Git.Commit.IsCommitRenamesSeparately")

internal var CommitContext.commitAuthor: VcsUser? by commitProperty(COMMIT_AUTHOR_KEY, null)
internal var CommitContext.commitAuthorDate: Date? by commitProperty(COMMIT_AUTHOR_DATE_KEY, null)
internal var CommitContext.isSignOffCommit: Boolean by commitProperty(IS_SIGN_OFF_COMMIT_KEY)
internal var CommitContext.isCommitRenamesSeparately: Boolean by commitProperty(IS_COMMIT_RENAMES_SEPARATELY_KEY)

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
  private var currentChangeList: LocalChangeList? = null

  private val panel = JPanel(GridBagLayout())
  private val authorField = VcsUserEditor(project, getKnownCommitAuthors())
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
      override fun focusLost(e: FocusEvent) {
        updateCurrentCommitAuthor()
        clearAuthorWarning()
      }
    })
    authorField.addHierarchyListener(object : HierarchyListener {
      override fun hierarchyChanged(e: HierarchyEvent) {
        if (e.isShowingChanged && authorField.isShowing && authorField.user != null) {
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

  override fun refresh() = refresh(null)

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
    refresh(list)

    panel.revalidate()
    panel.repaint()
  }

  private fun refresh(changeList: LocalChangeList?) {
    updateRenamesCheckboxState()
    clearAuthorWarning()

    currentChangeList = changeList
    setAuthor(changeList?.author)
    authorDate = changeList?.authorDate
  }

  fun getAuthor(): VcsUser? = authorField.user

  private fun setAuthor(author: VcsUser?) {
    val isAuthorNullOrDefault = author == null || isDefaultAuthor(author)
    authorField.user = author.takeUnless { isAuthorNullOrDefault }

    if (!isAuthorNullOrDefault) {
      authorField.putClientProperty("JComponent.outline", "warning")
      if (authorField.isShowing) showAuthorWarning()
    }
  }

  private fun updateCurrentCommitAuthor() {
    if (!commitPanel.isNonModalCommit) return

    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    val changeList = changeListManager.getChangeList(currentChangeList?.id) ?: return

    changeListManager.editChangeListData(changeList.name, ChangeListData(getAuthor(), authorDate))
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