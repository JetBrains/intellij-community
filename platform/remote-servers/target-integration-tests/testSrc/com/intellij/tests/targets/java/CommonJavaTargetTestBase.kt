// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests.targets.java

import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.ExecutionWithDebuggerToolsTestCase
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessEvents
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.target.RunTargetsEnabled
import com.intellij.execution.testframework.SearchForTestsTask
import com.intellij.execution.testframework.export.TestResultsXmlFormatter
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.java.execution.AbstractTestFrameworkIntegrationTest
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestModeFlags
import kotlinx.coroutines.*
import org.jdom.Content
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.function.Predicate
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(org.junit.runners.Parameterized::class)
abstract class CommonJavaTargetTestBase(protected val executionMode: ExecutionMode) : ExecutionWithDebuggerToolsTestCase() {
  /** A [com.intellij.execution.target.ContributedConfigurationBase.displayName] or null for the local target. */
  abstract val targetName: String?

  override fun initOutputChecker(): OutputChecker = OutputChecker({ testAppPath }, { appOutputPath })

  private var defaultForceCompilationInTests: Boolean? = null

  enum class ExecutionMode { RUN, DEBUG }

  companion object {
    @JvmStatic
    @org.junit.runners.Parameterized.Parameters(name = "{0}")
    fun data(): Collection<ExecutionMode> {
      return ExecutionMode.values().toList()
    }
  }

  protected fun getExecutor() = when (executionMode) {
    ExecutionMode.RUN -> DefaultRunExecutor.getRunExecutorInstance()
    ExecutionMode.DEBUG -> DefaultDebugExecutor.getDebugExecutorInstance()
  }

  public override fun setUp() {
    super.setUp()

    RunTargetsEnabled.forceEnable(testRootDisposable)

    (ExecutionManager.getInstance(project) as? ExecutionManagerImpl)?.let {
      defaultForceCompilationInTests = it.forceCompilationInTests
      it.forceCompilationInTests = true
    }
  }

  public override fun tearDown() {
    try {
      defaultForceCompilationInTests?.let {
        (ExecutionManager.getInstance(project) as? ExecutionManagerImpl)?.forceCompilationInTests = it
      }
    }
    finally {
      super.tearDown()
    }
  }

  protected fun getTestClass(className: String) = ReadAction.compute<PsiClass, Throwable> {
    JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
  }

  protected suspend fun ProcessHandler.collectOutput(demandZeroExitCode: Boolean = true,
                                                     handler: (event: ProcessEvent, outputType: Key<*>) -> Boolean): String =
    suspendCancellableCoroutine { continuation ->
      val wholeOutput = StringBuilder()
      val stdout = StringBuilder()
      addProcessListener(object : ProcessListener {
        override fun startNotified(event: ProcessEvent) {
          event.text?.let(wholeOutput::append)
        }

        override fun processTerminated(event: ProcessEvent) {
          event.text?.let(wholeOutput::append)
          if (event.exitCode == 0 || !demandZeroExitCode) {
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

  protected fun doTestJUnitRunConfiguration(runConfiguration: JUnitConfiguration,
                                            expectedTestsResultExported: String) {
    runWithConnectInUnitTestMode {
      runBlocking {
        val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          ExecutionEnvironmentBuilder(project, getExecutor()).runProfile(runConfiguration).build()
        }

        val textDeferred = processOutputReader(demandZeroExitCode = false) { _, _ ->
          true
        }

        if (executionMode == ExecutionMode.DEBUG) {
          createBreakpoints(runReadAction {
            getTestClass("SomeTest").containingFile
          })
          createBreakpoints(runReadAction {
            getTestClass("AlsoTest").containingFile
          })
        }

        var runContentDescriptor: RunContentDescriptor
        withTimeout(30_000) {
          runContentDescriptor = CompletableDeferred<RunContentDescriptor>()
            .also { deferred ->
              executionEnvironment.setCallback { deferred.complete(it) }
              withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
                executionEnvironment.runner.execute(executionEnvironment)
              }
            }
            .await()
        }

        val output = withTimeout(30_000) { textDeferred.await() }

        assertNotNull(runContentDescriptor)

        val smtRunnerConsoleView = runContentDescriptor.executionConsole as SMTRunnerConsoleView
        val resultsViewer = smtRunnerConsoleView.resultsViewer

        DebuggerManager.getInstance(project).getDebugProcess(runContentDescriptor.processHandler)?.let { process: DebugProcess ->
          (process as DebugProcessEvents).session?.let { session: DebuggerSession ->
            val sessionCompletable = CompletableDeferred<Unit>()
            session.contextManager.addListener { _, event ->
              if (event == DebuggerSession.Event.DISPOSE) {
                sessionCompletable.complete(Unit)
              }
            }
            if (session.state == DebuggerSession.State.DISPOSED) {
              sessionCompletable.complete(Unit)
            }
            withTimeout(30_000) {
              sessionCompletable.await()
            }
          }
        }

        val transformerFactory = TransformerFactory.newInstance() as SAXTransformerFactory
        val handler: TransformerHandler = transformerFactory.newTransformerHandler()
        handler.transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        handler.transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

        val writer = StringWriter()
        handler.setResult(StreamResult(writer))
        TestResultsXmlFormatter.execute(resultsViewer.root, runConfiguration, resultsViewer.properties, handler)
        val testrun = JDOMUtil.load(writer.toString())
        assertNotNull(writer.toString(), testrun)
        testrun.removeAttribute("footerText")
        testrun.removeAttribute("duration")
        testrun.getChild("root")?.removeChildren("output")
        testrun.removeChild("config")
        testrun.sortChildren { o1, o2 ->
          "${o1?.name} ${o1?.getAttributeValue("locationUrl")}".compareTo("${o2?.name} ${o2?.getAttributeValue("locationUrl")}")
        }
        for (child in testrun.getChildren("suite")) {
          child.removeAttribute("duration")
          for (testChild in child.getChildren("test")) {
            purifyTestNode(testChild, writer)
          }
        }
        for (testChild in testrun.getChildren("test")) {
          purifyTestNode(testChild, writer)
        }

        val expectedText = when (executionMode) {
          ExecutionMode.DEBUG -> {
            expectedTestsResultExported
          }
          else -> {
            val element = JDOMUtil.load(expectedTestsResultExported)
            element.getChildren("suite").forEach { suite ->
              suite.getChildren("test").forEach(::removeDebuggerOutput)
            }
            element.getChildren("test").forEach(::removeDebuggerOutput)
            JDOMUtil.write(element)
          }
        }
        assertEquals(output, expectedText, JDOMUtil.write(testrun))
      }
    }
  }

  private fun purifyTestNode(testNode: Element, writer: StringWriter) {
    testNode.removeAttribute("duration")

    // Stacktrace for LocalJavaTargetTest differs here due to usage of AppMainV2 in ProcessProxyFactoryImpl;
    // let's mask that; AppMainV2 won't be used in production, only when running from sources
    testNode.getChildren("output").forEach {
      val content = it.getContent(0)
      assertEquals(writer.toString(), Content.CType.Text, content.cType)
      val contentText = content.value
      it.setContent(Text(contentText.substring(0, contentText.length.coerceAtMost(600))))
    }
  }

  private fun removeDebuggerOutput(testElement: Element) {
    removeContentsPartially(testElement) {
      it?.text?.contains("Debugger:") ?: false
    }
  }

  protected fun processOutputReader(demandZeroExitCode: Boolean = true,
                                    filter: (ProcessEvent, Key<*>) -> Boolean): CompletableDeferred<String> {
    val textDeferred = CompletableDeferred<String>()
    RunConfigurationExtension.EP_NAME.point.registerExtension(
      object : RunConfigurationExtension() {
        override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

        override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
          configuration: T,
          params: JavaParameters,
          runnerSettings: RunnerSettings?
        ) {
        }

        override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
          GlobalScope.launch {
            textDeferred.complete(handler.collectOutput(demandZeroExitCode, filter))
          }
        }

        override fun getSerializationId(): String {
          return "JavaTargetTestBase::RunConfigurationExtension"
        }
      },
      LoadingOrder.ANY,
      testRootDisposable
    )
    return textDeferred
  }

  private fun removeContentsPartially(testElement: Element, predicate: Predicate<Element?>) {
    for (index in ((testElement.contentSize - 1) downTo 0)) {
      if (predicate.test(testElement.getContent(index) as Element)) {
        testElement.removeContent(index)
      }
    }
  }

  private fun runWithConnectInUnitTestMode(block: () -> Unit) {
    val connectInUnitTestModeValue = TestModeFlags.get(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY)
    TestModeFlags.set(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY, true)
    try {
      block()
    }
    finally {
      TestModeFlags.set(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY, connectInUnitTestModeValue)
    }
  }

  /**
   * Initializes the module within the given directory. The module content root
   * directory is expected to have the typical structure with `src` and `tests`
   * child directories.
   *
   * JUnit is added to the module as a Maven dependency.
   *
   * @param module the module
   * @param contentRoot the content entry of the module with `src` and `tests`
   *                    folders in it
   */
  protected fun initializeSampleModule(module: Module, contentRoot: VirtualFile) {
    val libraryDescriptors = listOf(
      JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0"),
      JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-engine", "5.3.0"),
      JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-engine", "1.7.0"),
      JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-launcher", "1.7.0"),
    )
    libraryDescriptors.forEach { libraryDescriptor ->
      AbstractTestFrameworkIntegrationTest.addMavenLibs(module, libraryDescriptor)
    }

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.addContentEntry(contentRoot)
      contentEntry.addSourceFolder(contentRoot.url + "/src", false)
      contentEntry.addSourceFolder(contentRoot.url + "/tests", true)
    }
  }
}