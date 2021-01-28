// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests.targets.java

import com.intellij.debugger.ExecutionWithDebuggerToolsTestCase
import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionManager
import com.intellij.execution.PsiLocation
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
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestModeFlags
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Content
import org.jdom.Text
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

  override fun getTestAppPath(): String = "${PathManager.getCommunityHomePath()}/platform/remote-servers/target-integration-tests/targetApp"

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
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PACKAGE)
    runWithConnectInUnitTestMode {
      doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                    "  <count name=\"total\" value=\"2\" />\n" +
                                                    "  <count name=\"failed\" value=\"1\" />\n" +
                                                    "  <count name=\"passed\" value=\"1\" />\n" +
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
                                                    "\tat AlsoTest.testShouldFail(AlsoTest.java:11)\n" +
                                                    "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                    "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                    "    </test>\n" +
                                                    "  </suite>\n" +
                                                    "</testrun>")
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

  @Test
  fun `test junit tests - run single test`() {
    val alsoTestClass = getAlsoTestClass()

    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_CLASS).also { conf ->
      conf.persistentData.setMainClass(alsoTestClass)
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                  "  <count name=\"total\" value=\"1\" />\n" +
                                                  "  <count name=\"failed\" value=\"1\" />\n" +
                                                  "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                                                  "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                  "      <diff actual=\"5\" expected=\"4\" />\n" +
                                                  "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                  "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                  "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                  "\tat AlsoTest.testShouldFail(AlsoTest.java:11)\n" +
                                                  "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                  "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                  "    </test>\n" +
                                                  "  </suite>\n" +
                                                  "</testrun>")
  }

  private fun getAlsoTestClass() = ReadAction.compute<PsiClass, Throwable> {
    JavaPsiFacade.getInstance(project).findClass("AlsoTest", GlobalSearchScope.allScope(project))
  }

  @Test
  fun `test junit tests - run tests in directory`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_DIRECTORY).also { conf ->
      conf.persistentData.dirName = "${PathManager.getCommunityHomePath()}/platform/remote-servers/target-integration-tests/targetApp/tests"
    }

    runWithConnectInUnitTestMode {
      @Suppress("SpellCheckingInspection", "GrazieInspection")
      doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                    "  <count name=\"total\" value=\"2\" />\n" +
                                                    "  <count name=\"failed\" value=\"1\" />\n" +
                                                    "  <count name=\"passed\" value=\"1\" />\n" +
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
                                                    "\tat AlsoTest.testShouldFail(AlsoTest.java:11)\n" +
                                                    "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                    "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                    "    </test>\n" +
                                                    "  </suite>\n" +
                                                    "</testrun>")
    }
  }

  @Test
  fun `test junit tests - run tests by pattern`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PATTERN).also { conf ->
      conf.persistentData.setPatterns(LinkedHashSet(listOf("^So.*")))
    }

    runWithConnectInUnitTestMode {
      @Suppress("SpellCheckingInspection", "GrazieInspection")
      doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                    "  <count name=\"total\" value=\"1\" />\n" +
                                                    "  <count name=\"passed\" value=\"1\" />\n" +
                                                    "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                    "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                    "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                                                    "  </suite>\n" +
                                                    "</testrun>")
    }
  }

  @Test
  fun `test junit tests - run test method by pattern`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PATTERN).also { conf ->
      conf.persistentData.setPatterns(LinkedHashSet(listOf("SomeTest,testSomething")))
    }

    runWithConnectInUnitTestMode {
      doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                    "  <count name=\"total\" value=\"1\" />\n" +
                                                    "  <count name=\"passed\" value=\"1\" />\n" +
                                                    "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                    "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                    "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                                                    "  </suite>\n" +
                                                    "</testrun>")
    }
  }

  private fun createJUnitConfiguration(testObject: String) = JUnitConfiguration("JUnit tests Run Configuration", project).also { conf ->
    conf.setModule(module)
    conf.defaultTargetName = targetName
    conf.persistentData.TEST_OBJECT = testObject
  }

  @Test
  fun `test junit tests - run test method`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_METHOD).also { conf ->
      conf.persistentData.setTestMethod(PsiLocation.fromPsiElement(getAlsoTestClass().findMethodsByName("testShouldFail", false)[0]))
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                  "  <count name=\"total\" value=\"1\" />\n" +
                                                  "  <count name=\"failed\" value=\"1\" />\n" +
                                                  "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                                                  "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                  "      <diff actual=\"5\" expected=\"4\" />\n" +
                                                  "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                  "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                  "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                  "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                  "\tat AlsoTest.testShouldFail(AlsoTest.java:11)\n" +
                                                  "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                  "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                  "    </test>\n" +
                                                  "  </suite>\n" +
                                                  "</testrun>")
  }

  @Test
  fun `test junit tests - run tagged tests`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_TAGS).also { conf ->
      conf.persistentData.tags = "selected"
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                  "  <count name=\"total\" value=\"1\" />\n" +
                                                  "  <count name=\"passed\" value=\"1\" />\n" +
                                                  "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                  "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                  "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                                                  "  </suite>\n" +
                                                  "</testrun>")
  }

  @Test
  fun `test junit tests - run tests by uniqueId`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_UNIQUE_ID).also { conf ->
      conf.persistentData.setUniqueIds("[engine:junit-jupiter]/[class:SomeTest]")
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration, "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                  "  <count name=\"total\" value=\"1\" />\n" +
                                                  "  <count name=\"passed\" value=\"1\" />\n" +
                                                  "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                  "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                  "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\" />\n" +
                                                  "  </suite>\n" +
                                                  "</testrun>")
  }

  private fun doTestJUnitRunConfiguration(runConfiguration: JUnitConfiguration, expectedTestsResultExported: String) {
    runBlocking {
      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        ExecutionEnvironmentBuilder(project, executor).runProfile(runConfiguration).build()
      }

      val textDeferred = processOutputReader(demandZeroExitCode = false) { _, _ ->
        true
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
      testrun.getChild("root")?.removeChild("output")
      testrun.removeChild("config")
      for (child in testrun.getChildren("suite")) {
        child.removeAttribute("duration")
        for (testChild in child.getChildren("test")) {
          testChild.removeAttribute("duration")

          // Stacktrace for LocalJavaTargetTest differs here due to usage of AppMainV2 in ProcessProxyFactoryImpl;
          // let's mask that; AppMainV2 won't be used in production, only when running from sources
          testChild.getChild("output")?.let {
            val content = it.getContent(0)
            assertEquals(writer.toString(), Content.CType.Text, content.cType)
            val contentText = content.value
            it.setContent(Text(contentText.substring(0, contentText.length.coerceAtMost(600))))
          }
        }
      }

      assertEquals(output, expectedTestsResultExported, JDOMUtil.write(testrun))
    }
  }
}