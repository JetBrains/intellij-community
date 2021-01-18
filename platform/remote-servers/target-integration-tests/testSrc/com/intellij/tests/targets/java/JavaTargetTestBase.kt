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
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.SearchForTestsTask
import com.intellij.execution.testframework.export.TestResultsXmlFormatter
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.java.execution.AbstractTestFrameworkIntegrationTest
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.junit.Test
import java.io.StringWriter
import java.util.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult
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

  override fun setUpModule() {
    super.setUpModule()

    val libraryDescriptor = JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0")
    AbstractTestFrameworkIntegrationTest.addMavenLibs(module, libraryDescriptor)

    val contentRoot = LocalFileSystem.getInstance().findFileByPath(
      "${PathManagerEx.getCommunityHomePath()}/platform/remote-servers/target-integration-tests/targetApp")!!
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.addContentEntry(contentRoot)
      contentEntry.addSourceFolder(contentRoot.url + "/src", false)
      contentEntry.addSourceFolder(contentRoot.url + "/tests", true)
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

  private fun processOutputReader(demandZeroExitCode: Boolean = true,
                                  filter: (ProcessEvent, Key<*>) -> Boolean): CompletableDeferred<String> {
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

  private suspend fun ProcessHandler.collectOutput(demandZeroExitCode: Boolean = true,
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

  @Test
  fun `test junit tests - run all`() {
    val connectInUnitTestModeProperty = System.getProperty(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY)
    System.setProperty(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY, "true")
    try {
      doTestRunRunAllJUnitTests()
    }
    finally {
      if (connectInUnitTestModeProperty == null) {
        System.clearProperty(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY)
      }
      else {
        System.setProperty(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY, connectInUnitTestModeProperty)
      }
    }
  }

  private fun doTestRunRunAllJUnitTests() {
    val runConfiguration = JUnitConfiguration("All JUnit tests Run Configuration", project).also { conf ->
      conf.setModule(module)
      conf.defaultTargetName = targetName

      conf.persistentData.changeList = "All"
      conf.persistentData.TEST_OBJECT = "package"
    }
    runBlocking {
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        ExecutionEnvironmentBuilder(project, executor).runProfile(runConfiguration).build()
      }

      val textDeferred = processOutputReader(demandZeroExitCode = false) { _, _ ->
        true
      }

      var runContentDescriptor: RunContentDescriptor
      // withDeletingExcessiveEditors {
      withTimeout(300_000) {
        runContentDescriptor = CompletableDeferred<RunContentDescriptor>()
          .also { deferred ->
            executionEnvironment.setCallback { deferred.complete(it) }
            withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
              executionEnvironment.runner.execute(executionEnvironment)
            }
          }
          .await()
      }
      //  }

      val output = withTimeout(300_000) { textDeferred.await() }

      assertNotNull(runContentDescriptor)

      val resultsViewer = (runContentDescriptor.executionConsole as SMTRunnerConsoleView).resultsViewer

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
      val rootChild = Objects.requireNonNull(testrun.getChild("root"), writer.toString())
      rootChild.removeChild("output")
      val configChild = Objects.requireNonNull(testrun.getChild("config"), writer.toString())
      configChild.removeChild("target")
      for (child in testrun.getChildren("suite")) {
        child.removeAttribute("duration")
        for (testChild in child.getChildren("test")) {
          testChild.removeAttribute("duration")
          testChild.removeAttribute("outp")
        }
      }

      assertEquals(output,
                   "<testrun name=\"All JUnit tests Run Configuration\">\n" +
                   "  <count name=\"total\" value=\"2\" />\n" +
                   "  <count name=\"failed\" value=\"1\" />\n" +
                   "  <count name=\"passed\" value=\"1\" />\n" +
                   "  <config configId=\"JUnit\" name=\"All JUnit tests Run Configuration\">\n" +
                   "    <module name=\"test junit tests - run all\" />\n" +
                   "    <option name=\"TEST_OBJECT\" value=\"package\" />\n" +
                   "    <method v=\"2\" />\n" +
                   "  </config>\n" +
                   "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                   "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                   "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                   "  </suite>\n" +
                   "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                   "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                   "      <diff actual=\"5\" expected=\"4\" />\n" +
                   "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                   "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                   "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                   "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                   "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                   "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                   "\tat AlsoTest.testShouldFail(AlsoTest.java:10)\n" +
                   "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                   "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n" +
                   "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n" +
                   "\tat java.base/java.lang.reflect.Method.invoke(Method.java:567)\n" +
                   "\tat org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:515)\n" +
                   "\tat org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:115)\n" +
                   "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda\$invokeTestMethod\$6(TestMethodTestDescriptor.java:171)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:72)\n" +
                   "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:167)\n" +
                   "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:114)\n" +
                   "\tat org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:59)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda\$executeRecursively\$5(NodeTestTask.java:131)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:72)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:127)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:107)\n" +
                   "\tat java.base/java.util.ArrayList.forEach(ArrayList.java:1540)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda\$executeRecursively\$5(NodeTestTask.java:136)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:72)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:127)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:107)\n" +
                   "\tat java.base/java.util.ArrayList.forEach(ArrayList.java:1540)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda\$executeRecursively\$5(NodeTestTask.java:136)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:72)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:127)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:107)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:32)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:52)\n" +
                   "\tat org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:51)\n" +
                   "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:220)\n" +
                   "\tat org.junit.platform.launcher.core.DefaultLauncher.lambda\$execute\$6(DefaultLauncher.java:188)\n" +
                   "\tat org.junit.platform.launcher.core.DefaultLauncher.withInterceptedStreams(DefaultLauncher.java:202)\n" +
                   "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:181)\n" +
                   "\tat org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:128)\n" +
                   "\tat com.intellij.junit5.JUnit5IdeaTestRunner.startRunnerWithArgs(JUnit5IdeaTestRunner.java:71)\n" +
                   "\tat com.intellij.rt.junit.IdeaTestRunner\$Repeater.startRunnerWithArgs(IdeaTestRunner.java:33)\n" +
                   "\tat com.intellij.rt.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:220)\n" +
                   "\tat com.intellij.rt.junit.JUnitStarter.main(JUnitStarter.java:53)</output>\n" +
                   "    </test>\n" +
                   "  </suite>\n" +
                   "</testrun>",
                   JDOMUtil.write(testrun))

    }
  }
}