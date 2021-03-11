// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.vcs

import com.intellij.CommonBundle
import com.intellij.execution.*
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile
import com.intellij.execution.testframework.TestsUIUtil
import com.intellij.execution.testframework.sm.ConfigurationBean
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ExceptionUtil
import com.intellij.util.PairConsumer
import com.intellij.util.ThrowableConvertor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val LOG = logger<RunTestsCheckinHandlerFactory>()

@State(name = "TestsVcsConfig", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class TestsVcsConfiguration : PersistentStateComponent<TestsVcsConfiguration.MyState> {
  class MyState {
    var enabled = false
    var configuration : ConfigurationBean? = null
  }
  var myState = MyState()
  override fun getState() = myState

  override fun loadState(state: MyState) {
    myState = state
  }
}

class RunTestsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    RunTestsBeforeCheckinHandler(panel)
}

class FailedTestCommitProblem(@NlsContexts.NotificationContent val statusText: String, val historyFileName: String) : CommitProblem {
  override val text: String
    get() {
      return statusText
    }
}

class RunTestsBeforeCheckinHandler(private val commitPanel: CheckinProjectPanel) :
  CheckinHandler(), CommitCheck<FailedTestCommitProblem> {

  private val project: Project get() = commitPanel.project
  private val settings: TestsVcsConfiguration get() = project.getService(TestsVcsConfiguration::class.java)

  override fun isEnabled(): Boolean = settings.myState.enabled

  override suspend fun runCheck(): FailedTestCommitProblem? {
    val configurationSettings = getConfiguredRunConfiguration() ?: return null
    
    return withContext(Dispatchers.IO) {
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val environment = ExecutionUtil.createEnvironment(executor, configurationSettings)?.build()
      val executionResult = environment?.state?.execute(executor, MyRunner()) ?: return@withContext null
      val handler = executionResult.processHandler ?: return@withContext null
      val resultsForm = executionResult.executionConsole?.component as? SMTestRunnerResultsForm ?: return@withContext null

      suspendCoroutine<Any?> { continuation ->
        resultsForm.addEventsListener(object : TestResultsViewer.EventsListener {
          override fun onTestingFinished(sender: TestResultsViewer) {
            continuation.resume(null)
          }
        })
        if (!handler.isStartNotified) handler.startNotify()
      }

      val rootNode = resultsForm.testsRootNode
      val commitProblem = if (rootNode.isDefect) {
        awaitSavingHistory(resultsForm)
        FailedTestCommitProblem(TestsUIUtil.getTestSummary(rootNode), resultsForm.historyFileName)
      }
      else null

      UIUtil.invokeLaterIfNeeded {
        Disposer.dispose(resultsForm)
      }

      return@withContext commitProblem
    }
  }

  private suspend fun awaitSavingHistory(resultsForm: SMTestRunnerResultsForm) {
    withTimeout(timeMillis = 600000) {
      while (getHistoryFile(resultsForm.historyFileName).second == null) {
        delay(timeMillis = 500)
      }
    }
  }

  private fun getConfiguredRunConfiguration(): RunnerAndConfigurationSettings? {
    val configurationBean = settings.myState.configuration?:return null
    return RunManager.getInstance(project).findConfigurationByTypeAndName(configurationBean.configurationId, configurationBean.name)
  }

  override fun showDetails(problem: FailedTestCommitProblem) {
    val (path, virtualFile) = getHistoryFile(problem.historyFileName)
    if (virtualFile != null) {
      AbstractImportTestsAction.doImport(project, virtualFile)
    }
    else {
      LOG.error("File not found: $path")
    }
  }

  private fun getHistoryFile(fileName: String): Pair<String, VirtualFile?> {
    val path = "${TestStateStorage.getTestHistoryRoot(project).path}/$fileName.xml"
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    return Pair(path, virtualFile)
  }


  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    val configurationBean = settings.myState.configuration
    val initialText = if (configurationBean != null) getOptionTitle(configurationBean.name)
    else SmRunnerBundle.message("checkbox.run.tests.before.commit.no.configuration")
    
    return object :
      BooleanCommitOption(commitPanel, initialText, true, settings.myState::enabled) {
      override fun getComponent(): JComponent {
        val showFiltersPopup = LinkListener<Any> { sourceLink, _ ->
          JBPopupMenu.showBelow(sourceLink, ActionPlaces.UNKNOWN, createConfigurationChooser())
        }
        val configureFilterLink = LinkLabel(SmRunnerBundle.message("link.label.choose.configuration.before.commit"), null, showFiltersPopup)
  
        return JBUI.Panels.simplePanel(4, 0).addToLeft(checkBox).addToCenter(configureFilterLink)
      }
  
      private fun createConfigurationChooser(): ActionGroup {
        val result = DefaultActionGroup()
        RunManager.getInstance(project).allConfigurationsList.groupBy { it.type }.forEach { (type, list) ->
          val localConfigurations: List<RunConfiguration> = list
            .filter {
              it is SMRunnerConsolePropertiesProvider && !(it is TargetEnvironmentAwareRunProfile && it.needPrepareTarget())
            }
          if (localConfigurations.isNotEmpty()) {
            result.addSeparator()
          }
          localConfigurations
            .forEach { configuration: RunConfiguration ->
              result.add(object : AnAction(configuration.name, null, configuration.icon) {
                override fun actionPerformed(e: AnActionEvent) {
                  val bean = ConfigurationBean()
                  bean.configurationId = type.id
                  bean.name = configuration.name
                  settings.myState.configuration = bean

                  checkBox.text = getOptionTitle(configuration.name)
                }
              })
            }
        }
        return result
      }
    }
  }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>) : ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    val runConfiguration = getConfiguredRunConfiguration()?: return ReturnResult.COMMIT
    val failedTests = RunTask(runConfiguration).getFailedTests()
    if (failedTests == null) return ReturnResult.COMMIT
    val commitActionText = StringUtil.removeEllipsisSuffix(executor?.actionText ?: commitPanel.commitActionName)
    return when (askToReview(failedTests, commitActionText)) {
      Messages.YES -> {
        showDetails(failedTests)
        ReturnResult.CLOSE_WINDOW
      }
      Messages.NO -> ReturnResult.COMMIT
      else -> ReturnResult.CANCEL
    }

  }

  private fun askToReview(failedTests: FailedTestCommitProblem,
                          @Nls commitActionText: String) =
    MessageDialogBuilder.yesNoCancel(SmRunnerBundle.message("checkbox.run.tests.before.commit.no.configuration"), failedTests.text)
      .icon(UIUtil.getWarningIcon())
      .yesText(VcsBundle.message("code.smells.review.button"))
      .noText(commitActionText)
      .cancelText(CommonBundle.getCancelButtonText())
      .show(project)

  @NlsContexts.DialogTitle
  private fun getOptionTitle(name: String): String {
    return SmRunnerBundle.message("checkbox.run.tests.before.commit", name)
  }

  inner class RunTask(private val runConfiguration : RunnerAndConfigurationSettings) 
    : Task.WithResult<FailedTestCommitProblem?, Exception>(project, getOptionTitle(runConfiguration.name), false) {

    fun getFailedTests(): FailedTestCommitProblem? {
      queue()

      return try {
        result
      }
      catch (e : ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        ExceptionUtil.rethrowUnchecked(e)
        throw RuntimeException(e)
      }
    }
    
    override fun compute(indicator: ProgressIndicator): FailedTestCommitProblem? {
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val environment = ExecutionUtil.createEnvironment(executor, runConfiguration)?.build()
      val executionResult = environment?.state?.execute(executor, MyRunner()) ?: return null
      val handler = executionResult.processHandler ?: return null
      val resultsForm = executionResult.executionConsole?.component as? SMTestRunnerResultsForm ?: return null

      val result = Ref<FailedTestCommitProblem?>() 
      resultsForm.addEventsListener(object : TestResultsViewer.EventsListener {
        override fun onTestingFinished(sender: TestResultsViewer) {
          val rootNode = sender.testsRootNode
          if (rootNode != null && rootNode.isDefect) {
            result.set(FailedTestCommitProblem(TestsUIUtil.getTestSummary(rootNode), (sender as SMTestRunnerResultsForm).historyFileName))
          }
        }

        override fun onTestNodeAdded(sender: TestResultsViewer, test: SMTestProxy) {
          indicator.text = SmRunnerBundle.message("progress.text.running.tests", getPresentableName(test))
        }

        private fun getPresentableName(test: SMTestProxy): String {
          val presentableName = test.presentableName
          val parent = test.parent
          return if (parent != null && parent !is SMTestProxy.SMRootTestProxy) {
            getPresentableName(parent) + "."+ presentableName
          }
          else presentableName
        }
      })

      if (!handler.isStartNotified) {
        handler.startNotify()
      }

      val threshold = System.currentTimeMillis() + 60000
      while (getHistoryFile(resultsForm.historyFileName).second == null) {
        Thread.sleep(500)
        if (System.currentTimeMillis() > threshold) {
          break
        }
      }
      
      UIUtil.invokeLaterIfNeeded {
        Disposer.dispose(resultsForm)
      }

      return result.get()
    }
  }
  
  inner class MyRunner : ProgramRunner<RunnerSettings?> {
    override fun getRunnerId(): String {
      return "MyRunner"
    }

    @Throws(ExecutionException::class)
    override fun execute(environment: ExecutionEnvironment) {

      val profileState = environment.state ?: return
      ExecutionManager.getInstance(environment.project)
        .startRunProfile(environment,
                         profileState,
                         ThrowableConvertor<RunProfileState, RunContentDescriptor?, ExecutionException> { state: RunProfileState? ->
                           state?.execute(DefaultRunExecutor.getRunExecutorInstance(), this)
                           return@ThrowableConvertor null
                         })
    }

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
      return true
    }
  }
}