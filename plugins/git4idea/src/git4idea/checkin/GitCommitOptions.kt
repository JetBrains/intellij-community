// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.author
import com.intellij.openapi.vcs.changes.authorDate
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.*
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserEditor
import com.intellij.vcs.log.VcsUserEditor.Companion.getAllUsers
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.util.VcsUserUtil.isSamePerson
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitUserRegistry
import git4idea.GitUtil.getRepositoryManager
import git4idea.checkin.GitCheckinEnvironment.Companion.collectActiveMovementProviders
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.UIManager

private val COMMIT_AUTHOR_KEY = Key.create<VcsUser>("Git.Commit.Author")
private val COMMIT_AUTHOR_DATE_KEY = Key.create<Date>("Git.Commit.AuthorDate")
private val IS_SIGN_OFF_COMMIT_KEY = Key.create<Boolean>("Git.Commit.IsSignOff")
private val IS_COMMIT_RENAMES_SEPARATELY_KEY = Key.create<Boolean>("Git.Commit.IsCommitRenamesSeparately")

var CommitContext.commitAuthor: VcsUser? by commitProperty(COMMIT_AUTHOR_KEY, null)
  internal set
var CommitContext.commitAuthorDate: Date? by commitProperty(COMMIT_AUTHOR_DATE_KEY, null)
  internal set
var CommitContext.isSignOffCommit: Boolean by commitProperty(IS_SIGN_OFF_COMMIT_KEY)
  internal set
var CommitContext.isCommitRenamesSeparately: Boolean by commitProperty(IS_COMMIT_RENAMES_SEPARATELY_KEY)
  internal set

private val HierarchyEvent.isShowingChanged get() = (changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L
private val HierarchyEvent.isParentChanged get() = (changeFlags and HierarchyEvent.PARENT_CHANGED.toLong()) != 0L

private val CheckinProjectPanel.commitAuthorTracker: CommitAuthorTracker? get() = commitWorkflowHandler.commitAuthorTracker

class GitCommitOptionsUi(
  private val commitPanel: CheckinProjectPanel,
  private val commitContext: CommitContext,
  private val showAmendOption: Boolean
) : RefreshableOnComponent,
    CheckinChangeListSpecificComponent,
    AmendCommitModeListener,
    CommitAuthorListener,
    Disposable {

  private val project get() = commitPanel.project
  private val settings = GitVcsSettings.getInstance(project)
  private val userRegistry = GitUserRegistry.getInstance(project)
  val amendHandler: AmendCommitHandler get() = commitPanel.commitWorkflowHandler.amendCommitHandler

  private var authorDate: Date? = null

  private val panel: JComponent
  private val authorField = VcsUserEditor(project, getKnownCommitAuthors())
  private val signOffCommit = JBCheckBox(GitBundle.message("commit.options.sign.off.commit.checkbox"),
                                         settings.shouldSignOffCommit()).apply {
    val user = commitPanel.roots.mapNotNull { userRegistry.getUser(it) }.firstOrNull()
    val signature = user?.let { escapeXmlEntities(VcsUserUtil.toExactString(it)) }.orEmpty()
    toolTipText = XmlStringUtil.wrapInHtml(GitBundle.message("commit.options.sign.off.commit.message.line", signature))
    addActionListener {
      CommitSessionCollector.getInstance(project).logCommitOptionToggled(CommitOption.SIGN_OFF, isSelected)
    }
  }
  private val commitRenamesSeparately = JBCheckBox(
    GitBundle.message("commit.options.create.extra.commit.with.file.movements"),
    settings.isCommitRenamesSeparately
  )

  private var authorWarning: Balloon? = null

  init {
    panel = buildLayout()

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
    if (commitPanel.isNonModalCommit) {
      commitPanel.commitAuthorTracker?.addCommitAuthorListener(this, this)

      panel.addHierarchyListener { e ->
        if (e.isParentChanged && panel == e.changed && panel.parent != null) beforeShow()
      }
    }

    amendHandler.addAmendCommitModeListener(this, this)
  }

  private fun buildLayout() = panel {
    val disposable = this@GitCommitOptionsUi
    row(GitBundle.message("commit.author")) {
      cell(authorField)
        .align(AlignX.FILL)
    }
    if (showAmendOption) {
      row {
        cell(ToggleAmendCommitOption(commitPanel, disposable))
      }
    }
    row {
      cell(signOffCommit)
    }
    row {
      cell(commitRenamesSeparately)
    }
  }

  // called before popup size calculation => changing preferred size here will be correctly reflected by popup
  private fun beforeShow() = updateRenamesCheckboxState()

  override fun amendCommitModeToggled() = updateRenamesCheckboxState()

  override fun dispose() = Unit

  override fun getComponent(): JComponent = panel

  override fun restoreState() {
    updateRenamesCheckboxState()
    clearAuthorWarning()

    commitAuthorChanged()
    commitAuthorDateChanged()
  }

  override fun saveState() {
    if (commitPanel.isNonModalCommit) updateRenamesCheckboxState()
    val author = getAuthor()

    commitContext.commitAuthor = author
    commitContext.commitAuthorDate = authorDate
    commitContext.isSignOffCommit = signOffCommit.isSelected
    commitContext.isCommitRenamesSeparately = commitRenamesSeparately.run { isEnabled && isSelected }

    author?.let { settings.saveCommitAuthor(it.toString()) }
    settings.setSignOffCommit(signOffCommit.isSelected)
    settings.isCommitRenamesSeparately = commitRenamesSeparately.isSelected
  }

  override fun onChangeListSelected(list: LocalChangeList) {
    updateRenamesCheckboxState()
    clearAuthorWarning()

    setAuthor(list.author)
    authorDate = list.authorDate

    panel.revalidate()
    panel.repaint()
  }

  fun getAuthor(): VcsUser? = authorField.user

  private fun setAuthor(author: VcsUser?) {
    val isAuthorNullOrDefault = author == null || isDefaultAuthor(author)
    authorField.user = author.takeUnless { isAuthorNullOrDefault }

    if (!isAuthorNullOrDefault) {
      authorField.putClientProperty("JComponent.outline", "warning") // NON-NLS
      if (authorField.isShowing) showAuthorWarning()
    }
  }

  private fun updateCurrentCommitAuthor() {
    commitPanel.commitAuthorTracker?.commitAuthor = getAuthor()
  }

  override fun commitAuthorChanged() {
    val newAuthor = commitPanel.commitAuthorTracker?.commitAuthor
    if (getAuthor() != newAuthor) setAuthor(newAuthor)
  }

  override fun commitAuthorDateChanged() {
    authorDate = commitPanel.commitAuthorTracker?.commitAuthorDate
  }

  private fun updateRenamesCheckboxState() {
    val providers = collectActiveMovementProviders(project)

    commitRenamesSeparately.apply {
      text = providers.singleOrNull()?.description ?: GitBundle.message("commit.options.create.extra.commit.with.file.movements")
      isVisible = providers.isNotEmpty()
      isEnabled = isVisible && !amendHandler.isAmendCommitMode
    }
  }

  private fun showAuthorWarning() {
    if (authorWarning?.isDisposed == false) return

    val builder = JBPopupFactory.getInstance()
      .createBalloonBuilder(JLabel(GitBundle.message("commit.author.diffs")))
      .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets")) // NON-NLS
      .setBorderColor(JBUI.CurrentTheme.Validator.warningBorderColor())
      .setFillColor(JBUI.CurrentTheme.Validator.warningBackgroundColor())
      .setHideOnClickOutside(true)
      .setHideOnFrameResize(false)

    authorWarning = builder.createBalloon()
    authorWarning?.show(RelativePoint(authorField, Point(authorField.width / 2, authorField.height)), Balloon.Position.below)
  }

  private fun clearAuthorWarning() {
    authorField.putClientProperty("JComponent.outline", null) // NON-NLS
    authorWarning?.hide()
    authorWarning = null
  }

  private fun getKnownCommitAuthors(): List<String> = (getAllUsers(project) + settings.commitAuthors).distinct().sorted()

  private fun isDefaultAuthor(author: VcsUser): Boolean {
    val repositoryManager = getRepositoryManager(project)
    val affectedRoots = commitPanel.roots.filter { repositoryManager.getRepositoryForRootQuick(it) != null }

    return affectedRoots.isNotEmpty() &&
           affectedRoots.map { userRegistry.getUser(it) }.all { it != null && isSamePerson(author, it) }
  }
}