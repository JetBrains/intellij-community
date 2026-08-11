// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class JUnitOldSuiteApiTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Parameterized.Parameters(name = "junit-jupiter-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[]{JUnitRtConstants.JUNIT5_VERSION, JUnitRtConstants.JUNIT5_PLATFORM_VERSION},
                         new Object[]{JUnitRtConstants.JUNIT6_VERSION, JUnitRtConstants.JUNIT6_PLATFORM_VERSION});
  }

  @Parameterized.Parameter
  public String myJupiterVersion;

  @Parameterized.Parameter(1)
  public String myPlatformVersion;

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/oldSuiteApi");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", myJupiterVersion), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", myPlatformVersion),
                 repoManager);
  }

  @Test
  public void runClass() throws Exception {
    ProcessOutput processOutput = doStartTestsProcess(createRunClassConfiguration("org.example.impl.MyTest"));
    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);

    List<ServiceMessage> messages = processOutput.messages;

    Map<String, TestStarted> tests = getStartedTests(messages);
    assertEquals(Set.of("java:test://org.example.impl.MyTest/test"),
                 tests.values().stream().map(t -> t.getAttributes().get("locationHint")).collect(Collectors.toSet()));
    assertEquals(Set.of(), getTestIds(messages, TestFailed.class));
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


  private static Map<String, TestStarted> getStartedTests(List<ServiceMessage> messages) {
    return messages.stream().filter(TestStarted.class::isInstance).map(TestStarted.class::cast)
      .collect(Collectors.toMap(t -> t.getAttributes().get("id"), t -> t));
  }


  private static <T extends BaseTestMessage> Set<String> getTestIds(List<ServiceMessage> messages, Class<T> clazz) {
    return messages.stream().filter(clazz::isInstance).map(clazz::cast).map(o -> o.getAttributes().get("id")).collect(Collectors.toSet());
  }
}