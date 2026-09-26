// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

public class JUnit4WallTimeIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/wallTime");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "4.13.2"), getRepoManager());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Registry.get("test.use.suite.duration").resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testWallTimeEnabled() throws ExecutionException {
    Registry.get("test.use.suite.duration").setValue(true);

    PsiClass psiClass = findClass(myModule, "org.example.WallTimeSuiteTest");
    assertNotNull(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(createConfiguration(psiClass));

    ServiceMessage suite = ContainerUtil.find(processOutput.messages, TestSuiteFinished.class::isInstance);
    long duration = Long.parseLong(suite.getAttributes().get("duration"));
    // @AfterClass (200ms) + @Test (100ms) = >= 300ms
    assertTrue("Suite duration should be >= 300ms (@Test 100ms + @AfterClass 200ms) but was " + duration, duration >= 300);
  }

  public void testEnclosedSuiteIncludesBeforeClassInWallTime() throws ExecutionException {
    Registry.get("test.use.suite.duration").setValue(true);

    PsiClass psiClass = findClass(myModule, "org.example.WallTimeEnclosedSuiteTest");
    assertNotNull(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(createConfiguration(psiClass));

    ServiceMessage suite = ContainerUtil.find(processOutput.messages, m -> m instanceof TestSuiteFinished && "InnerTest".equals(m.getAttributes().get("name")));
    assertNotNull(suite);
    long duration = Long.parseLong(suite.getAttributes().get("duration"));
    // @BeforeClass (250ms) + two @Test methods (150ms each) = >= 550ms
    assertTrue("InnerTest suite duration should be >= 550ms (@BeforeClass 250ms + tests 300ms) but was " + duration, duration >= 550);
  }

  public void testWallTimeDisabled() throws ExecutionException {
    Registry.get("test.use.suite.duration").setValue(false);

    PsiClass psiClass = findClass(myModule, "org.example.WallTimeSuiteTest");
    assertNotNull(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(createConfiguration(psiClass));

    ServiceMessage enteredMatrix = ContainerUtil.find(processOutput.messages, m -> "enteredTheMatrix".equals(m.getMessageName()));
    assertNull(enteredMatrix.getAttributes().get("durationStrategy"));

    ServiceMessage suite = ContainerUtil.find(processOutput.messages, TestSuiteFinished.class::isInstance);
    assertNull(suite.getAttributes().get("duration"));
  }
}