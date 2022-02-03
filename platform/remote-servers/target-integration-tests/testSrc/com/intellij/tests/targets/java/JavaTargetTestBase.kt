// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests.targets.java

import com.intellij.execution.PsiLocation
import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Create a subclass to briefly test a compatibility of some Run Target with Java run configurations.
 */
abstract class JavaTargetTestBase(executionMode: ExecutionMode) : CommonJavaTargetTestBase(executionMode) {
  /** One test checks that the being launched on the target, a test application can read the file at this path. */
  abstract val targetFilePath: String

  /** Expected contents of [targetFilePath]. */
  abstract val targetFileContent: String

  override fun getTestAppPath(): String = "${PlatformTestUtil.getCommunityPath()}/platform/remote-servers/target-integration-tests/targetApp"

  override fun setUpModule() {
    super.setUpModule()

    val contentRoot = LocalFileSystem.getInstance().findFileByPath(testAppPath)
    initializeSampleModule(module, contentRoot!!)
  }

  @Test
  fun `test can read file at target`(): Unit = runBlocking {
    if (executionMode != ExecutionMode.RUN) return@runBlocking
    doTestCanReadFileAtTarget(ShortenCommandLine.NONE)
  }

  @Test
  fun `test can read file at target with manifest shortener`(): Unit = runBlocking {
    if (executionMode != ExecutionMode.RUN) return@runBlocking
    doTestCanReadFileAtTarget(ShortenCommandLine.MANIFEST)
  }

  @Test
  fun `test can read file at target with args file shortener`(): Unit = runBlocking {
    if (executionMode != ExecutionMode.RUN) return@runBlocking
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
  fun `test java application`(): Unit = runBlocking {
    val cwd = tempDir.createDir()
    val executionEnvironment: ExecutionEnvironment = withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      ExecutionEnvironmentBuilder(project, getExecutor()).runProfile(
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

    if (executionMode == ExecutionMode.DEBUG) {
      createBreakpoints(runReadAction {
        getTestClass("Cat").containingFile
      })
    }

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
    val expectedText = when (executionMode) {
      ExecutionMode.DEBUG -> "Debugger: $targetFileContent\n$targetFileContent"
      else -> targetFileContent
    }
    assertThat(text).isEqualTo(expectedText)
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

    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"2\" />\n" +
                                                              "  <count name=\"failed\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                                                              "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                              "      <diff actual=\"5\" expected=\"4\" />\n" +
                                                              "      <output type=\"stdout\">Debugger: testShouldFail() reached</output>\n" +
                                                              "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                              "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                              "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                              "\tat AlsoTest.testShouldFail(AlsoTest.java:12)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run single test`() {
    val alsoTestClass = getTestClass("AlsoTest")

    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_CLASS).also { conf ->
      conf.persistentData.setMainClass(alsoTestClass)
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"failed\" value=\"1\" />\n" +
                                                              "  <root name=\"AlsoTest\" location=\"java:suite://AlsoTest\" />\n" +
                                                              "  <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                              "    <diff actual=\"5\" expected=\"4\" />\n" +
                                                              "    <output type=\"stdout\">Debugger: testShouldFail() reached</output>\n" +
                                                              "    <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                              "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                              "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                              "\tat AlsoTest.testShouldFail(AlsoTest.java:12)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                              "  </test>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run tests in directory`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_DIRECTORY).also { conf ->
      conf.persistentData.dirName = "$testAppPath/tests"
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"2\" />\n" +
                                                              "  <count name=\"failed\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://AlsoTest\" name=\"AlsoTest\" status=\"failed\">\n" +
                                                              "    <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                              "      <diff actual=\"5\" expected=\"4\" />\n" +
                                                              "      <output type=\"stdout\">Debugger: testShouldFail() reached</output>\n" +
                                                              "      <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                              "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                              "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                              "\tat AlsoTest.testShouldFail(AlsoTest.java:12)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run tests by pattern`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PATTERN).also { conf ->
      conf.persistentData.setPatterns(LinkedHashSet(listOf("^So.*")))
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run test method by pattern`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_PATTERN).also { conf ->
      conf.persistentData.setPatterns(LinkedHashSet(listOf("SomeTest,testSomething")))
    }

    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  private fun createJUnitConfiguration(testObject: String) = JUnitConfiguration("JUnit tests Run Configuration", project).also { conf ->
    conf.setModule(module)
    conf.defaultTargetName = targetName
    conf.persistentData.TEST_OBJECT = testObject
  }

  @Test
  fun `test junit tests - run test method`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_METHOD).also { conf ->
      conf.persistentData.setTestMethod(PsiLocation.fromPsiElement(getTestClass("AlsoTest").findMethodsByName("testShouldFail", false)[0]))
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"failed\" value=\"1\" />\n" +
                                                              "  <root name=\"AlsoTest\" location=\"java:suite://AlsoTest\" />\n" +
                                                              "  <test locationUrl=\"java:test://AlsoTest/testShouldFail\" name=\"testShouldFail()\" metainfo=\"\" status=\"failed\">\n" +
                                                              "    <diff actual=\"5\" expected=\"4\" />\n" +
                                                              "    <output type=\"stdout\">Debugger: testShouldFail() reached</output>\n" +
                                                              "    <output type=\"stderr\">org.opentest4j.AssertionFailedError: \n" +
                                                              "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:54)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:195)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:152)\n" +
                                                              "\tat org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:147)\n" +
                                                              "\tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:326)\n" +
                                                              "\tat AlsoTest.testShouldFail(AlsoTest.java:12)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                                                              "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(</output>\n" +
                                                              "  </test>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run tagged tests`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_TAGS).also { conf ->
      conf.persistentData.tags = "selected"
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }

  @Test
  fun `test junit tests - run tests by uniqueId`() {
    val runConfiguration = createJUnitConfiguration(JUnitConfiguration.TEST_UNIQUE_ID).also { conf ->
      conf.persistentData.setUniqueIds("[engine:junit-jupiter]/[class:SomeTest]")
    }

    @Suppress("SpellCheckingInspection", "GrazieInspection")
    doTestJUnitRunConfiguration(runConfiguration = runConfiguration,
                                expectedTestsResultExported = "<testrun name=\"JUnit tests Run Configuration\">\n" +
                                                              "  <count name=\"total\" value=\"1\" />\n" +
                                                              "  <count name=\"passed\" value=\"1\" />\n" +
                                                              "  <root name=\"&lt;default package&gt;\" location=\"java:suite://&lt;default package&gt;\" />\n" +
                                                              "  <suite locationUrl=\"java:suite://SomeTest\" name=\"SomeTest\" status=\"passed\">\n" +
                                                              "    <test locationUrl=\"java:test://SomeTest/testSomething\" name=\"testSomething()\" metainfo=\"\" status=\"passed\">\n" +
                                                              "      <output type=\"stdout\">Debugger: testSomething() reached</output>\n" +
                                                              "    </test>\n" +
                                                              "  </suite>\n" +
                                                              "</testrun>")
  }
}
