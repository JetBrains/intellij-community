// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.DateFormatUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitNotificationIdsHolder
import git4idea.commands.Git
import git4idea.config.GitSaveChangesPolicy
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.stash.GitChangesSaver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes a Git operation on a number of repositories surrounding it by stash-unstash procedure.
 * I.e. stashes changes, executes the operation and then unstashes it.
 */
class GitPreservingProcess @ApiStatus.Internal constructor(
  private val project: Project,
  private val git: Git,
  private val rootsToSave: Collection<VirtualFile>,
  private val operationTitle: @Nls String,
  private val destinationName: @Nls String,
  saveMethod: GitSaveChangesPolicy,
  private val progressIndicator: ProgressIndicator,
  reportLocalHistoryActivity: Boolean,
  private val operation: Runnable,
) {
  @Nls
  private val stashMessage: @Nls String = VcsBundle.message(
    "stash.changes.message.with.date",
    StringUtil.capitalize(operationTitle),
    DateFormatUtil.formatDateTime(Clock.getTime())
  )
  private val saver: GitChangesSaver = configureSaver(saveMethod, reportLocalHistoryActivity)

  private val isLoaded = AtomicBoolean()

  constructor(
    project: Project,
    git: Git,
    rootsToSave: Collection<VirtualFile>,
    operationTitle: @Nls String,
    destinationName: @Nls String,
    saveMethod: GitSaveChangesPolicy,
    indicator: ProgressIndicator,
    operation: Runnable,
  ) : this(project, git, rootsToSave, operationTitle, destinationName, saveMethod, indicator, true, operation)

  @JvmOverloads
  fun execute(autoLoadDecision: Computable<Boolean>? = null) {
    val operation = Runnable {
      val savedSuccessfully: Boolean = ProgressManager.getInstance().computeInNonCancelableSection(ThrowableComputable { save() })
      LOG.debug("save result: $savedSuccessfully")
      if (savedSuccessfully) {
        try {
          LOG.debug("running operation")
          operation.run()
          LOG.debug("operation completed.")
        }
        finally {
          if (autoLoadDecision == null || autoLoadDecision.compute()) {
            LOG.debug("loading")
            ProgressManager.getInstance().executeNonCancelableSection(Runnable { load() })
          }
          else {
            saver.notifyLocalChangesAreNotRestored(operationTitle)
          }
        }
      }
      LOG.debug("finished.")
    }

    GitFreezingProcess(project, operationTitle, operation).execute()
  }

  /**
   * Configures the saver: i.e. notifications and texts for the GitConflictResolver used inside.
   */
  private fun configureSaver(saveMethod: GitSaveChangesPolicy, reportLocalHistoryActivity: Boolean): GitChangesSaver {
    val saver = GitChangesSaver.getSaver(project, git, progressIndicator, stashMessage, saveMethod, reportLocalHistoryActivity)
    val mergeDialogCustomizer: MergeDialogCustomizer = object : MergeDialogCustomizer() {
      override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): @NlsContexts.Label String {
        return XmlStringUtil.wrapInHtml(
          GitBundle.message(
            "restore.conflict.dialog.description.label.text",
            operationTitle,
            XmlStringUtil.wrapInHtmlTag(destinationName, "code")
          )
        )
      }

      override fun getLeftPanelTitle(file: VirtualFile): @NlsContexts.Label String {
        return saveMethod.selectBundleMessage(
          GitBundle.message("restore.conflict.diff.dialog.left.stash.title"),
          GitBundle.message("restore.conflict.diff.dialog.left.shelf.title")
        )
      }

      override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): @NlsContexts.Label String {
        return XmlStringUtil.wrapInHtml(
          GitBundle.message(
            "restore.conflict.diff.dialog.right.title",
            XmlStringUtil.wrapInHtmlTag(destinationName, "b")
          )
        )
      }
    }

    val params =
      GitConflictResolver.Params(project).setReverse(true).setMergeDialogCustomizer(mergeDialogCustomizer)
        .setErrorNotificationTitle(GitBundle.message("preserving.process.local.changes.not.restored.error.title"))

    saver.setConflictResolverParams(params)
    return saver
  }

  /**
   * Saves local changes. In case of error shows a notification and returns false.
   */
  private fun save(): Boolean {
    val errorMessage = saver.saveLocalChangesOrError(rootsToSave)
    if (errorMessage == null) {
      return true
    }

    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.COULD_NOT_SAVE_UNCOMMITTED_CHANGES,
      GitBundle.message("save.notification.failed.title", operationTitle),
      errorMessage
    )
    return false
  }

  fun load() {
    if (isLoaded.compareAndSet(false, true)) {
      saver.load()
    }
    else {
      LOG.info("The changes were already loaded")
    }
  }

  companion object {
    private val LOG = logger<GitPreservingProcess>()
  }
}
