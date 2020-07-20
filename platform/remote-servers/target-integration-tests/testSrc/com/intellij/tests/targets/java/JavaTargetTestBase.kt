// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests.targets.java

import com.intellij.debugger.ExecutionWithDebuggerToolsTestCase
import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Create a subclass to briefly test a compatibility of some Run Target with Java run configurations.
 */
abstract class JavaTargetTestBase : ExecutionWithDebuggerToolsTestCase() {
  /** A [com.intellij.execution.target.ContributedConfigurationBase.displayName] or null for the local target. */
  abstract val targetName: String?

  /** One test checks that the being launched on the target, a test application can read the file at this path. */
  abstract val targetFilePath: String

  /** Expected contents of [targetFilePath]. */
  abstract val targetFileContent: String

  override fun initOutputChecker(): OutputChecker = OutputChecker(testAppPath, appOutputPath)

  override fun getTestAppPath(): String =
    PathManager.getCommunityHomePath() + "/platform/remote-servers/target-integration-tests/targetApp"

  private var defaultTargetsEnabled: Boolean? = null
  private var defaultForceCompilationInTests: Boolean? = null

  override fun setUp() {
    super.setUp()

    defaultTargetsEnabled = Experiments.getInstance().isFeatureEnabled("run.targets")
    Experiments.getInstance().setFeatureEnabled("run.targets", true)

    (ExecutionManager.getInstance(project) as? ExecutionManagerImpl)?.let {
      defaultForceCompilationInTests = it.forceCompilationInTests
      it.forceCompilationInTests = true
    }
  }

  override fun tearDown() {
    try {
      defaultTargetsEnabled?.let {
        Experiments.getInstance().setFeatureEnabled("run.targets", it)
      }
      defaultForceCompilationInTests?.let {
        (ExecutionManager.getInstance(project) as? ExecutionManagerImpl)?.forceCompilationInTests = it
      }
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `test can read file at target`(): Unit = runBlocking {
    doTestCanReadFileAtTarget(ShortenCommandLine.NONE)
  }

  @Test
  fun `test can read file at target with manifest shortener`(): Unit = runBlocking {
    doTestCanReadFileAtTarget(ShortenCommandLine.MANIFEST)
  }

  @Test
  fun `test can read file at target with args file shortener`(): Unit = runBlocking {
    doTestCanReadFileAtTarget(ShortenCommandLine.ARGS_FILE)
  }

  private suspend fun doTestCanReadFileAtTarget(shortenCommandLine: ShortenCommandLine) {
    val cwd = tempDir.createDir()
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      ExecutionEnvironmentBuilder(project, executor)
        .runProfile(
          ApplicationConfiguration("CatRunConfiguration", project).also { conf ->
            conf.setModule(module)
            conf.workingDirectory = cwd.toString()
            conf.mainClassName = "Cat"
            conf.programParameters = targetFilePath
            conf.defaultTargetName = targetName
            conf.shortenCommandLine = shortenCommandLine
          }
        )
        .build()
    }
    val textDeferred = processOutputReader { _, outputType -> outputType == ProcessOutputType.STDOUT }
    withDeletingExcessiveEditors {
      withTimeout(30_000) {
        CompletableDeferred<RunContentDescriptor>()
          .also { deferred ->
            executionEnvironment.setCallback { deferred.complete(it) }
            withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
              executionEnvironment.runner.execute(executionEnvironment)
            }
          }
          .await()
      }
    }
    val text = withTimeout(30_000) { textDeferred.await() }
    assertThat(text).isEqualTo(targetFileContent)
  }

  @Test
  fun `test java debugger`(): Unit = runBlocking {
    assertThat(SystemInfo.IS_AT_LEAST_JAVA9).describedAs("The test is intended to verifying Java 9 options.").isTrue()
    val cwd = tempDir.createDir()
    val executor = DefaultDebugExecutor.getDebugExecutorInstance()
    val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      ExecutionEnvironmentBuilder(project, executor)
        .runProfile(
          ApplicationConfiguration("CatRunConfiguration", project).also { conf ->
            conf.setModule(module)
            conf.workingDirectory = cwd.toString()
            conf.mainClassName = "Cat"
            conf.programParameters = targetFilePath
            conf.defaultTargetName = targetName
          }
        )
        .build()
    }

    createBreakpoints(runReadAction {
      JavaPsiFacade.getInstance(project)
        .findClass("Cat", GlobalSearchScope.allScope(project))!!
        .containingFile
    })

    val textDeferred = processOutputReader filter@{ event, outputType ->
      val text = event.text ?: return@filter false
      when (outputType) {
        ProcessOutputType.STDOUT ->
          // For some reason this string appears in stdout. Don't know whether it should appear or not.
          !text.startsWith("Listening for transport ")
        ProcessOutputType.SYSTEM -> text.startsWith("Debugger")
        else -> false
      }
    }

    withDeletingExcessiveEditors {
      withTimeout(30_000) {
        CompletableDeferred<RunContentDescriptor>()
          .also { deferred ->
            executionEnvironment.setCallback { deferred.complete(it) }
            withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
              executionEnvironment.runner.execute(executionEnvironment)
            }
          }
          .await()
      }
    }
    val text = withTimeout(30_000) { textDeferred.await() }
    assertThat(text).isEqualTo("Debugger: $targetFileContent\n$targetFileContent")
    Unit
  }

  private fun processOutputReader(filter: (ProcessEvent, Key<*>) -> Boolean): CompletableDeferred<String> {
    val textDeferred = CompletableDeferred<String>()
    RunConfigurationExtension.EP_NAME.point.registerExtension(
      object : RunConfigurationExtension() {
        override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

        override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
          configuration: T,
          params: JavaParameters,
          runnerSettings: RunnerSettings?
        ): Unit = Unit

        override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
          GlobalScope.launch {
            textDeferred.complete(handler.collectOutput(filter))
          }
        }
      },
      LoadingOrder.ANY,
      testRootDisposable
    )
    return textDeferred
  }

  private suspend fun ProcessHandler.collectOutput(handler: (event: ProcessEvent, outputType: Key<*>) -> Boolean): String =
    suspendCancellableCoroutine { continuation ->
      val wholeOutput = StringBuilder()
      val stdout = StringBuilder()
      addProcessListener(object : ProcessListener {
        override fun startNotified(event: ProcessEvent) {
          event.text?.let(wholeOutput::append)
        }

        override fun processTerminated(event: ProcessEvent) {
          event.text?.let(wholeOutput::append)
          if (event.exitCode == 0) {
            continuation.resume(stdout.toString())
          }
          else {
            continuation.resumeWithException(IllegalStateException("\n=== CONSOLE ===\n$wholeOutput\n=== CONSOLE END ==="))
          }
        }

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          event.text?.let(wholeOutput::append)
          if (handler(event, outputType)) {
            event.text?.let(stdout::append)
          }
        }
      })
    }

  private suspend inline fun withDeletingExcessiveEditors(handler: () -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editorsBefore = editorFactory.allEditors.filterTo(hashSetOf()) { it.project === project }
    try {
      handler()
    }
    finally {
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        for (editor in editorFactory.allEditors) {
          if (editor.project === project && editor !in editorsBefore) {
            editorFactory.releaseEditor(editor)
          }
        }
      }
    }
  }
}