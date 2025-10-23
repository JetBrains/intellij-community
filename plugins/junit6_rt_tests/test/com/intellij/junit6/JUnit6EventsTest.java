// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.junit6.ServiceMessageUtil.replaceAttributes;

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

    List<ServiceMessage> messages = output.messages;

    Map<String, TestStarted> tests = getStartedTests(messages);
    // Ensure our test method started with a proper location hint
    TestStarted started = tests.values().stream()
      .filter(t -> "java:test://com.intellij.junit6.testData.MyTestClass/test1".equals(t.getAttributes().get("locationHint")))
      .findFirst().orElse(null);
    assertNotNull(started);
    assertEquals("java:test://com.intellij.junit6.testData.MyTestClass/test1", started.getAttributes().get("locationHint"));
    String nodeId = started.getAttributes().get("nodeId");

    // There should be multiple failures reported for the same test
    List<ServiceMessage> failed = messages.stream()
      .filter(m -> nodeId.equals(m.getAttributes().get("nodeId")))
      .filter(m -> m instanceof TestFailed).toList();
    assertSize(3, failed);

    Set<String> errors = failed.stream().filter(m -> m.getAttributes().get("expected") != null)
      .map(m -> "expected='" + m.getAttributes().get("expected") + "', actual='" + m.getAttributes().get("actual") + "'")
      .collect(Collectors.toSet());
    assertEquals(Set.of(
      "expected='expected1', actual='actual1'",
      "expected='expected2', actual='actual2'"
    ), errors);

    // StdOut message should include our published entries
    Set<String> outs = messages.stream()
      .filter(m -> nodeId.equals(m.getAttributes().get("nodeId")))
      .filter(m -> m instanceof TestStdOut)
      .map(m -> m.getAttributes().get("out"))
      .map(s -> s.replaceAll("timestamp = [0-9\\-:.T]+", "timestamp = ##timestamp##"))
      .collect(Collectors.toSet());
    assertEquals(Set.of("timestamp = ##timestamp##, key1 = value1, stdout = out1\n"), outs);
  }

  public void testContainerFailure() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunMethodConfiguration("com.intellij.junit6.testData.MyTestClass", "brokenStream"));
    assertEmpty(output.err);

    // Expect a configuration failure
    List<String> test = output.messages.stream().filter(BaseTestMessage.class::isInstance)
      .map(BaseTestMessage.class::cast)
      .filter(m -> m.getTestName().equals("Class Configuration"))
      .map(m -> replaceAttributes(m, Map.of("details", "##details##")))
      .toList();
    assertEquals(List.of(
      "##teamcity[testStarted name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']",
      "##teamcity[testFailed name='Class Configuration' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' error='true' message='java.lang.IllegalStateException: broken' details='##details##']",
      "##teamcity[testFinished name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']"
    ), test);
  }

  public void testContainerDisabled() throws Exception {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("com.intellij.junit6.testData.MyTestClass"));
    assertEmpty(output.err);

    List<String> tests = output.messages.stream().filter(m -> m instanceof BaseTestMessage)
      .map(m -> replaceAttributes(m, Map.of(
        "timestamp", "##timestamp##",
        "duration", "##duration##",
        "message", "##message##",
        "details", "##details##"
      )))
      .map(s -> s.replaceAll("timestamp = [0-9\\-:.T]+", "timestamp = ##timestamp##")).toList();

    assertEquals(List.of(
      "##teamcity[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' name='disabledTest()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/disabledTest' metainfo='']",
      "##teamcity[testIgnored name='disabledTest()' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0' message='##message##']",
      "##teamcity[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' name='disabledTest()' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:disabledTest()|]' parentNodeId='0']",
      "##teamcity[testStarted id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' locationHint='java:test://com.intellij.junit6.testData.MyTestClass/test1' metainfo='org.junit.jupiter.api.TestReporter']",
      "##teamcity[testStdOut id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' out='timestamp = ##timestamp##, key1 = value1, stdout = out1|n']",
      "##teamcity[testFailed name='test1(TestReporter)' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' expected='expected1' actual='actual1' details='##details##']",
      "##teamcity[testFailed name='test1(TestReporter)' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' expected='expected2' actual='actual2' details='##details##']",
      "##teamcity[testFailed name='test1(TestReporter)' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##' message='##message##' details='##details##']",
      "##teamcity[testFinished id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' name='test1(TestReporter)' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[method:test1(org.junit.jupiter.api.TestReporter)|]' parentNodeId='0' duration='##duration##']",
      "##teamcity[testIgnored name='brokenStreamDisabled()' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStreamDisabled()|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStreamDisabled()|]' parentNodeId='0' message='##message##']",
      "##teamcity[testStarted name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']",
      "##teamcity[testFailed name='Class Configuration' id='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0' error='true' message='##message##' details='##details##']",
      "##teamcity[testFinished name='Class Configuration' nodeId='|[engine:junit-jupiter|]/|[class:com.intellij.junit6.testData.MyTestClass|]/|[test-factory:brokenStream()|]' parentNodeId='0']"
      ), tests);
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
