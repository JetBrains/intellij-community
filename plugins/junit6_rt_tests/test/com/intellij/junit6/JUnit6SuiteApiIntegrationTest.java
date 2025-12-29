// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
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

public class JUnit6SuiteApiIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit6_rt_tests/testData/integration/suiteApi");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", JUnit6Constants.VERSION), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", JUnit6Constants.VERSION), repoManager);
  }

  public void testRunClass() throws Exception {
    ProcessOutput processOutput = doStartTestsProcess(createRunClassConfiguration("org.example.api.SmokeSuite"));
    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);

    List<ServiceMessage> messages = processOutput.messages;

    Map<String, TestStarted> tests = getStartedTests(messages);
    assertEquals(Set.of("java:test://org.example.impl.MyTest/test"),
                 tests.values().stream().map(t -> t.getAttributes().get("locationHint")).collect(Collectors.toSet()));
    assertEquals(Set.of(), getTestIds(messages, TestFailed.class));
    assertEquals(tests.keySet(), getTestIds(messages, TestFinished.class));
  }

  public void testRunPackage() throws Exception {
    ProcessOutput processOutput = doStartTestsProcess(createRunPackageConfiguration("org.example.api"));
    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);

    List<ServiceMessage> messages = processOutput.messages;

    Map<String, TestStarted> tests = getStartedTests(messages);
    assertEquals(Set.of("java:test://org.example.impl.MyTest/test",
                        "java:test://org.example.impl.FirstTest/test1",
                        "java:test://org.example.impl.FirstTest/test2",
                        "java:test://org.example.impl.SecondTest/test1",
                        "java:test://org.example.impl.SecondTest/test2"),
                 tests.values().stream().map(t -> t.getAttributes().get("locationHint")).collect(Collectors.toSet()));
    assertEquals(getTestIds(tests, Set.of("java:test://org.example.impl.FirstTest/test1",
                                          "java:test://org.example.impl.SecondTest/test2")),
                 getTestIds(messages, TestFailed.class));
    assertEquals(tests.keySet(), getTestIds(messages, TestFinished.class));
  }

  private @NotNull RunConfiguration createRunClassConfiguration(final String className) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  private @NotNull RunConfiguration createRunPackageConfiguration(final String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
    assertNotNull(aPackage);

    RunConfiguration configuration = createConfiguration(aPackage);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  private static Map<String, TestStarted> getStartedTests(List<ServiceMessage> messages) {
    return messages.stream().filter(TestStarted.class::isInstance).map(TestStarted.class::cast)
      .collect(Collectors.toMap(t -> t.getAttributes().get("id"), t -> t, (existing, replacement) -> existing));
  }

  private static <T extends BaseTestMessage> Set<String> getTestIds(Map<String, TestStarted> tests, Set<String> locationHints) {
    return tests.entrySet().stream()
      .filter(e -> locationHints.contains(e.getValue().getAttributes().get("locationHint")))
      .map(t -> t.getKey())
      .collect(Collectors.toSet());
  }

  private static <T extends BaseTestMessage> Set<String> getTestIds(List<ServiceMessage> messages, Class<T> clazz) {
    return messages.stream().filter(clazz::isInstance).map(clazz::cast).map(o -> o.getAttributes().get("id")).collect(Collectors.toSet());
  }

  public void testRerunFailedFromSuite() throws Exception {
    ProcessOutput initialOutput = doStartTestsProcess(createRunPackageConfiguration("org.example.api"));
    assertEmpty(initialOutput.out);
    assertEmpty(initialOutput.err);

    List<ServiceMessage> initialMessages = initialOutput.messages;
    Map<String, TestStarted> started = getStartedTests(initialMessages);
    Set<String> failedIds = getTestIds(initialMessages, TestFailed.class);

    Set<String> failedHints = failedIds.stream()
      .map(id -> started.get(id))
      .map(t -> t.getAttributes().get("locationHint"))
      .collect(Collectors.toSet());

    assertEquals(Set.of(
      "java:test://org.example.impl.FirstTest/test1",
      "java:test://org.example.impl.SecondTest/test2"
    ), failedHints);

    ProcessOutput rerunOutput = doStartTestsProcess(createRunPackageConfiguration("org.example.api"), failedHints);
    assertEmpty(rerunOutput.out);
    assertEmpty(rerunOutput.err);

    Map<String, TestStarted> rerunStarted = getStartedTests(rerunOutput.messages);
    Set<String> rerunHints = rerunStarted.values().stream().map(t -> t.getAttributes().get("locationHint")).collect(Collectors.toSet());

    assertEquals(failedHints, rerunHints);
    assertEquals(rerunStarted.keySet(), getTestIds(rerunOutput.messages, TestFinished.class));
  }

  public void testInitSuiteFailed() throws ExecutionException {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("org.example.failed.SuiteFailed"));
    assertEmpty(output.err);

    String tests = output.messages.stream().filter(m -> m instanceof MessageWithAttributes)
      .map(m -> replaceAttributes(m, Map.of(
        "timestamp", "##timestamp##",
        "duration", "##duration##",
        "message", "##message##",
        "details", "##details##"
      )))
      .map(m -> m.replaceAll("##teamcity\\[", "##TC["))
      .map(s -> s.replaceAll("timestamp = [0-9\\-:.T]+", "timestamp = ##timestamp##"))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[testStarted id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' parentNodeId='0' locationHint='java:suite://org.example.failed.SuiteFailed']
                   ##TC[testFailed id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' parentNodeId='0' error='true' message='##message##' details='##details##']
                   ##TC[testFinished id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[synthetic:method:configuration()|]' parentNodeId='0']
                   ##TC[testStarted id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' locationHint='java:test://org.example.failed.FailedTest/test' metainfo='']
                   ##TC[testIgnored id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]']
                   ##TC[testFinished id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteFailed|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]']""",
                 tests);
  }

  public void testInitSubSuiteFailed() throws ExecutionException {
    ProcessOutput output = doStartTestsProcess(createRunClassConfiguration("org.example.failed.SuiteOk"));
    assertEmpty(output.err);

    String tests = output.messages.stream().filter(m -> m instanceof MessageWithAttributes)
      .map(m -> replaceAttributes(m, Map.of(
        "timestamp", "##timestamp##",
        "duration", "##duration##",
        "message", "##message##",
        "details", "##details##"
      )))
      .map(m -> m.replaceAll("##teamcity\\[", "##TC["))
      .map(s -> s.replaceAll("timestamp = [0-9\\-:.T]+", "timestamp = ##timestamp##"))
      .collect(Collectors.joining("\n"));

    assertEquals("""
                   ##TC[testSuiteStarted id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' name='FailedTest' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' parentNodeId='0' locationHint='java:suite://org.example.failed.FailedTest']
                   ##TC[testStarted id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' locationHint='java:suite://org.example.failed.FailedTest']
                   ##TC[testFailed id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' error='true' message='##message##' details='##details##']
                   ##TC[testFinished id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' name='Class Configuration' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[synthetic:method:configuration()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]']
                   ##TC[testStarted id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' locationHint='java:test://org.example.failed.FailedTest/test' metainfo='']
                   ##TC[testIgnored id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]']
                   ##TC[testFinished id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' name='test()' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]/|[method:test()|]' parentNodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]']
                   ##TC[testSuiteFinished id='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' name='FailedTest' nodeId='|[engine:junit-platform-suite|]/|[suite:org.example.failed.SuiteOk|]/|[engine:junit-jupiter|]/|[class:org.example.failed.FailedTest|]' parentNodeId='0']""",
                 tests);
  }
}