// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

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

public class JUnit5SuiteApiIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/suiteApi");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test1", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.12.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", "1.12.2"),
                 repoManager);
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

  @NotNull
  private RunConfiguration createRunClassConfiguration(final String className) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  @NotNull
  private RunConfiguration createRunPackageConfiguration(final String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
    assertNotNull(aPackage);

    RunConfiguration configuration = createConfiguration(aPackage);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  private static Map<String, TestStarted> getStartedTests(List<ServiceMessage> messages) {
    return messages.stream().filter(TestStarted.class::isInstance).map(TestStarted.class::cast)
      .collect(Collectors.toMap(t -> t.getAttributes().get("id"), t -> t));
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
}