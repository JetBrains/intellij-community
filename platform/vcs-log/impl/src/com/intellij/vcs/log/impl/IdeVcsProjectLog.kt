// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.asSafely
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.history.VcsLogFileHistoryUiProvider
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToCommit
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToHash
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.VcsLogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import java.util.concurrent.CancellationException

private val LOG: Logger = logger<IdeVcsProjectLog>()

internal class IdeVcsProjectLog(
  project: Project,
  coroutineScope: CoroutineScope,
) : VcsProjectLogBase<IdeVcsLogManager>(project, coroutineScope) {

  private val mainUiHolderState = MutableStateFlow<MainUiHolder?>(null)
  @Deprecated("Use VcsProjectLog.runInMainLog or get the ui from DataContext via VcsLogDataKeys.VCS_LOG_UI. As a last resort - VcsProjectLog.mainUi")
  override val mainLogUi: VcsLogUiImpl? = logManager?.mainUi as? VcsLogUiImpl

  init {
    initListeners()
  }

  override suspend fun createLogManager(logProviders: Map<VirtualFile, VcsLogProvider>): IdeVcsLogManager {
    val uiProperties = project.serviceAsync<VcsLogProjectTabsProperties>()
    return IdeVcsLogManager(project, coroutineScope, mainUiHolderState, uiProperties, logProviders) { s, t ->
      reinitOnErrorAsync(s, t)
    }.apply {
      withContext(Dispatchers.EDT) {
        createUi()
      }
    }
  }

  fun setMainUiHolder(holder: MainUiHolder?) {
    if (!coroutineScope.isActive) return
    mainUiHolderState.value = holder
  }

  override fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? {
    return logManager?.openNewLogTab(VcsLogTabLocation.TOOL_WINDOW, filters)
  }

  override fun showRevisionAsync(root: VirtualFile, hash: Hash, filePath: FilePath?): Deferred<Boolean> =
    coroutineScope.async {
      val progressTitle = VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash.toShortString())
      withBackgroundProgress(project, progressTitle) {
        val manager = init(true)
        if (filePath != null) {
          manager?.showCommit(hash, root, filePath, true)
        }
        else {
          manager?.showCommit(hash, root, true)
        } ?: false
      }
    }

  private suspend fun IdeVcsLogManager.showCommit(hash: Hash, root: VirtualFile, requestFocus: Boolean): Boolean =
    withContext(Dispatchers.EDT) {
      if (isDisposed) return@withContext false
      showCommitInLogTab(hash, root, requestFocus) { true } != null
    }

  private suspend fun IdeVcsLogManager.showCommit(hash: Hash, root: VirtualFile, filePath: FilePath, requestFocus: Boolean): Boolean =
    withContext(Dispatchers.EDT) {
      if (isDisposed) return@withContext false
      val logUi = showCommitInLogTab(hash, root, false) { logUi ->
        // Structure filter might prevent us from navigating to FilePath
        val hasFilteredChanges = logUi.properties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
                                 logUi.properties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] &&
                                 !logUi.properties.getFilterValues(VcsLogFilterCollection.STRUCTURE_FILTER.name).isNullOrEmpty()
        return@showCommitInLogTab !hasFilteredChanges
      } ?: return@withContext false

      logUi.selectFilePath(filePath, requestFocus)
      true
    }

  override fun showRevisionInMainLog(root: VirtualFile, hash: Hash) {
    runInMainUi { it.jumpToCommit(hash, root, false, true) }
  }

  override fun showRevisionInMainLog(hash: Hash) {
    runInMainUi {
      val future = it.jumpToHash(hash.asString(), false, true)
      if (!future.isDone) {
        coroutineScope.launch {
          val title = VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash.toShortString())
          withBackgroundProgress(project, title, false) {
            try {
              future.await()
            }
            catch (ce: CancellationException) {
              throw ce
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }
        }
      }
    }
  }

  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?): Boolean {
    return VcsLogFileHistoryUiProvider.select(project, paths, revisionNumber) != null
  }

  override fun openFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?) {
    VcsLogFileHistoryUiProvider.select(project, paths, revisionNumber)?.showFileHistoryUi(project, paths, revisionNumber)
  }

  override fun openFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?, revisionToSelect: VcsRevisionNumber) {
    val ui = VcsLogFileHistoryUiProvider.select(project, paths, revisionNumber)?.showFileHistoryUi(project, paths, revisionNumber, false) ?: return
    val future = ui.jumpToHash(revisionToSelect.asString(), false, true)

    coroutineScope.launch {
      val title = VcsLogBundle.message("file.history.show.commit.in.history.process", VcsLogUtil.getShortHash(revisionToSelect.asString()))
      withBackgroundProgress(project, title, true) {
        future.await()
      }
    }
  }

  override fun runInMainUi(consumer: (MainVcsLogUi) -> Unit) {
    doRunWhenLogIsReady {
      it.runInMainUi {
        consumer(it)
      }
    }
  }

  interface MainUiHolder {
    fun installMainUi(manager: IdeVcsLogManager, ui: MainVcsLogUi)
  }

  class InitLogStartupActivity : ProjectActivity {
    init {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      getInstance(project).init(false)
    }
  }
}

internal class VcsLogToolwindowManagerListener(private val project: Project) : ToolWindowManagerListener {
  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (toolWindow.id == ChangesViewContentManager.TOOLWINDOW_ID) {
      val projectLog = VcsProjectLog.getInstance(project).asSafely<IdeVcsProjectLog>()
                       ?: run {
                         LOG.warn("Incorrect project log instance")
                         return
                       }
      projectLog.initAsync(true)
      projectLog.logManager?.toolWindowShown(toolWindow)
    }
  }
}
