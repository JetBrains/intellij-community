// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.Arrays;

public class JUnit4ParameterizedIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/parameterized");
  }

  private static Object[] createParams(final String mavenId, String src, String paramString) {
    return new Object[]{mavenId, src, paramString};
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit:junit:4.13"), getRepoManager());
  }

  //IDEA-146578
  public void testParameterizedPattern() throws ExecutionException {
    JUnitConfiguration configuration = new JUnitConfiguration("pattern", myProject);
    PsiClass psiClass = findClass(myModule, "a.BaseTest");
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName("simple", false)[0];
    configuration.bePatternConfiguration(Arrays.asList(findClass(myModule, "a.Test1"),
                                                       findClass(myModule, "a.Test2")), testMethod);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    assertTrue(testOutput, testOutput.contains("Test11"));
    assertTrue(testOutput, testOutput.contains("Test12"));
    assertTrue(testOutput, testOutput.contains("Test12"));
    assertTrue(testOutput, testOutput.contains("Test22"));
    assertFalse(testOutput, testOutput.contains("another"));
  }

  public void testSingleMethod() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "a.Test3");
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName("simple", false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);

    configuration.setProgramParameters("[0]");
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    assertTrue(testOutput, testOutput.contains("Test3[1]"));
  }
}
