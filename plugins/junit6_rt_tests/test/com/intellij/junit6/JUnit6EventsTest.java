// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.idea.IJIgnore;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.junit6.ServiceMessageUtil.normalizedTestOutput;

@SuppressWarnings("SSBasedInspection")
public class JUnit6EventsTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit6_rt_tests/testData/integration/events");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", JUnit6Constants.VERSION),
                 repoManager);
  }

  public void testMultipleFailures() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunMethodConfiguration("com.intellij.junit6.testData.MyTestClass", "test1"));
    assertEmpty(output.err);

    String tests = output.messages.stream().filter(m -> m instanceof BaseTestMessage)
      .map(m -> normalizedTestOutput(m, Map.of("details", (value) -> {
        int idx = value.indexOf("\n", 2);
        return idx > 0 ? value.substring(0, idx).trim().replaceAll(":[0-9]+", ":<line>") : value;
      })))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/test1' metainfo='org.junit.jupiter.api.TestReporter']
                   ##TC[testStdOut id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' out='timestamp = ##timestamp##, key1 = value1, stdout = out1|n']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='message1 ==> expected: <expected1> but was: <actual1>|nComparison Failure: ' expected='expected1' actual='actual1' details='org.opentest4j.AssertionFailedError: message1 ==> expected: <expected1> but was: <actual1>']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='message2 ==> expected: <expected2> but was: <actual2>|nComparison Failure: ' expected='expected2' actual='actual2' details='org.opentest4j.AssertionFailedError: message2 ==> expected: <expected2> but was: <actual2>']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='2 errors (2 failures)|n	org.opentest4j.AssertionFailedError: message1 ==> expected: <expected1> but was: <actual1>|n	org.opentest4j.AssertionFailedError: message2 ==> expected: <expected2> but was: <actual2>' details='org.opentest4j.MultipleFailuresError: 2 errors (2 failures)']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##']""",
                 tests);
  }

  public void testContainerFailure() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunMethodConfiguration("com.intellij.junit6.testData.MyTestClass", "brokenStream"));
    assertEmpty(output.err);

    // Expect a configuration failure
    String test = output.messages.stream().filter(BaseTestMessage.class::isInstance)
      .map(BaseTestMessage.class::cast)
      .filter(m -> m.getTestName().equals("Class Configuration"))
      .map(m -> normalizedTestOutput(m, Map.of("details", (value) -> {
        int idx = value.indexOf("\n", 2);
        return idx > 0 ? value.substring(0, idx).trim().replaceAll(":[0-9]+", ":<line>") : value;
      })))
      .collect(Collectors.joining("\n"));
    assertEquals("""
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/brokenStream' metainfo='']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' error='true' message='java.lang.IllegalStateException: broken' details='at com.intellij.junit6.testData.MyTestClass.brokenStream(MyTestClass.java:<line>)']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']""",
                 test);
  }

  public void testContainerDisabled() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("com.intellij.junit6.testData.MyTestClass"));
    assertEmpty(output.err);

    String tests = output.messages.stream().filter(m -> m instanceof BaseTestMessage)
      .map(m -> normalizedTestOutput(m, Map.of(
        "message", (value) -> "##message##",
        "details", (value) -> "##details##"
      )))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' name='disabledTest()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/disabledTest' metainfo='']
                   ##TC[testIgnored id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' name='disabledTest()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0' message='##message##']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' name='disabledTest()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/test1' metainfo='org.junit.jupiter.api.TestReporter']
                   ##TC[testStdOut id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' out='timestamp = ##timestamp##, key1 = value1, stdout = out1|n']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' expected='expected1' actual='actual1' details='##details##']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' expected='expected2' actual='actual2' details='##details##']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' details='##details##']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##']
                   ##TC[testIgnored id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStreamDisabled()|]' name='brokenStreamDisabled()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStreamDisabled()|]' parentNodeId='0' message='##message##']
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/brokenStream' metainfo='']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' error='true' message='##message##' details='##details##']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']""",
                 tests);
  }

  @IJIgnore(issue = "TW-71208")
  public void testStopExecution() throws Exception {
    ProcessOutput output =
      doStartTestsProcessAsync(createRunClassConfiguration("com.intellij.junit6.testData.CancelCheckTest"), Collections.emptySet());
    OSProcessHandler process = output.process;

    {
      // tests should be started - wait for lock file path in output
      Path lock = getLock(output);

      // stop process (SIGINT)
      OSProcessUtil.terminateProcessGracefully(process.getProcess());

      // wait for shutdown hook to execute
      Thread.sleep(1000);
      unlock(lock);

      // wait finalization
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      process.waitFor(10000);
      process.destroyProcess();
    }
    String tests = output.messages.stream().filter(m -> m instanceof BaseTestMessage)
      .map(m -> normalizedTestOutput(m, Map.of(
        "message", (value) -> "##message##",
        "details", (value) -> "##details##"
      ))).collect(Collectors.joining("\n"));

    assertEmpty(output.err);
    assertEquals(
      """
        ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test1()|]' name='test1()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test1()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.CancelCheckTest/test1' metainfo='']
        ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test1()|]' name='test1()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test1()|]' parentNodeId='0' duration='##duration##']
        ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.CancelCheckTest/test2' metainfo='']
        ##TC[testIgnored id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' parentNodeId='0' message='##message##']
        ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test2()|]' parentNodeId='0']
        ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' name='test3()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.CancelCheckTest/test3' metainfo='']
        ##TC[testIgnored id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' name='test3()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' parentNodeId='0' message='##message##']
        ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' name='test3()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.CancelCheckTest|]/|[method:test3()|]' parentNodeId='0']""",
      tests);
    assertTrue("@AfterAll must finish the tests correctly", output.out.contains("finish"));
  }

  private static void unlock(@NotNull Path lock) throws IOException {
    // unlock tests
    if (Files.exists(lock)) {
      Files.delete(lock);
    }
    else {
      fail("tests not started");
    }
  }

  private static @NotNull Path getLock(ProcessOutput output) throws InterruptedException {
    int maxIterations = 100;
    Path lock = null;
    while (lock == null && maxIterations-- > 0) {
      Thread.sleep(100);
      lock = output.out.stream()
        .filter(line -> line.startsWith("lock:"))
        .map(line -> line.substring("lock:".length()).trim())
        .map(Path::of)
        .findFirst()
        .orElse(null);
    }
    if (lock == null) {
      fail("tests not started - lock file path not received in time");
    }
    return lock;
  }

  public void testFailWithMessage() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("com.intellij.junit6.testData.SimpleFailTest"));
    assertEmpty(output.err);

    assertEmpty(output.err);

    String tests = output.messages.stream().filter(m -> m instanceof BaseTestMessage)
      .map(m -> normalizedTestOutput(m, Map.of("details", (value) -> {
        int idx = value.indexOf("\n", 2);
        return idx > 0 ? value.substring(0, idx).trim().replaceAll(":[0-9]+", ":<line>") : value;
      })))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' name='test1()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.SimpleFailTest/test1' metainfo='']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' name='test1()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' parentNodeId='0' duration='##duration##' message='org.opentest4j.AssertionFailedError: 123' details='at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:<line>)']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' name='test1()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test1()|]' parentNodeId='0' duration='##duration##']
                   ##TC[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.SimpleFailTest/test2' metainfo='']
                   ##TC[testFailed id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' parentNodeId='0' duration='##duration##' message='expected: <123> but was: <321>|nComparison Failure: ' expected='123' actual='321' details='org.opentest4j.AssertionFailedError: expected: <123> but was: <321>']
                   ##TC[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' name='test2()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.SimpleFailTest|]/|[method:test2()|]' parentNodeId='0' duration='##duration##']""",
                 tests);
  }

  public void testEscaping() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunMethodConfiguration("com.intellij.junit6.testData.AnnotationsTestClass", "test1"));
    assertEmpty(output.err);

    Map<String, TestStarted> tests = getStartedTests(output.messages);
    TestStarted started = tests.values().stream()
      .filter(t -> t.getAttributes().get("locationHint").equals("java:test://com.intellij.junit6.testData.AnnotationsTestClass/test1"))
      .findFirst().orElse(null);
    assertNotNull(started);
    // TeamCity service messages encode names internally; here we ensure the original display name is preserved
    assertEquals("test's method", started.getAttributes().get("name"));
  }

  @NotNull
  private RunConfiguration createRunMethodConfiguration(final String className, final String methodName) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
    RunConfiguration configuration = createConfiguration(method);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  @NotNull
  private RunConfiguration createRunClassConfiguration(final String className) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    RunConfiguration configuration = createConfiguration(aClass);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  private static Map<String, TestStarted> getStartedTests(List<ServiceMessage> messages) {
    return messages.stream().filter(TestStarted.class::isInstance).map(TestStarted.class::cast)
      .collect(Collectors.toMap(t -> t.getAttributes().get("id"), t -> t));
  }
}
