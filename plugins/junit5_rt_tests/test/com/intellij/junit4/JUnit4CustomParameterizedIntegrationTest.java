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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class JUnit4CustomParameterizedIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  public static final String CLASS_NAME = "a.Test1";
  private static final String METHOD_NAME = "simple";
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/params/") + mySrc;
  }

  @Parameterized.Parameters(name = "{1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(createParams("com.tngtech.junit.dataprovider:junit4-dataprovider:2.6", "dataProvider", "[1: 1]"),
                         createParams("com.carrotsearch.randomizedtesting:randomizedtesting-runner:2.7.8", "randomizedtesting", "[value=1]"),
                         createParams("com.google.testparameterinjector:test-parameter-injector:1.3", "testparameterinjector", "[1]"),
                         createParams("com.google.testparameterinjector:test-parameter-injector:1.3", "testparameterinjectorfield", "[a=1]")
    );
  }

  private static Object[] createParams(final String mavenId, String src, String paramString) {
    return new Object[]{mavenId, src, paramString};
  }

  @Parameterized.Parameter
  public String myMavenId;

  @Parameterized.Parameter(1)
  public String mySrc;

  @Parameterized.Parameter(2)
  public String myParamString;

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor(myMavenId), getRepoManager());
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit:junit:4.12"), getRepoManager());
  }

  @Test
  public void executeOneParameter() throws ExecutionException {
    ProcessOutput processOutput = doStartProcess(myParamString);
    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    assertTrue(testOutput, testOutput.contains("Test1"));
    assertFalse(testOutput, testOutput.contains("Test2"));
  }

  @Test
  public void executeNoParameters() throws ExecutionException {
    ProcessOutput processOutput = doStartProcess(null);
    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    assertTrue(testOutput, testOutput.contains("Test1"));
    assertTrue(testOutput, testOutput.contains("Test2"));
  }

  private ProcessOutput doStartProcess(String paramString) throws ExecutionException {
    PsiClass psiClass = findClass(myModule, CLASS_NAME);
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    configuration.setProgramParameters(paramString);
    return doStartTestsProcess(configuration);
  }
}
