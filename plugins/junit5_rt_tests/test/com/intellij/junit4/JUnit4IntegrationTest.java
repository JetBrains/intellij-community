// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkIntegrationTest;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class JUnit4IntegrationTest extends AbstractTestFrameworkIntegrationTest {

  public static final String CLASS_NAME = "a.Test1";
  private static final String METHOD_NAME = "simple";
  
  @Rule public final TestName myNameRule = new TestName();

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(createParams("4.12"),
                         createParams("4.11"),
                         createParams("4.10"),
                         createParams("4.9"),
                         createParams("4.8.2"),
                         createParams("4.5"),
                         createParams("4.4")
    );
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Parameterized.Parameter
  public String myJUnitVersion;

  @Before
  public void before() throws Exception {
    Module module = createEmptyModule();
    String communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
    String methodName = myNameRule.getMethodName();
    methodName = methodName.substring(0, methodName.indexOf("["));
    String testDataPath = communityPath + File.separator + "plugins" + File.separator + "junit5_rt_tests" +
                          File.separator + "testData" + File.separator + "integration" + File.separator + methodName;

    addMavenLibs(module, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", myJUnitVersion), getRepoManager());

    Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(module, jdk);
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(testDataPath));
      contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(testDataPath + File.separator + "test"), true);
      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPathForTests(VfsUtilCore.pathToUrl(testDataPath + File.separator + "out"));
    });
  }

  @After
  public void after() {
    JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
  }

  @Test
  public void ignoredTestMethod() throws ExecutionException {
    ProcessOutput processOutput = doStartProcess();
    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    switch (myJUnitVersion) {
      case "4.4", "4.5" -> {
        //shouldn't work for old versions
      }
      default -> {
        assertTrue(testOutput, testOutput.contains("Test1"));
        for (ServiceMessage message : processOutput.messages) {
          assertFalse(message.toString().contains("Ignored"));
        }
      }
    }
  }

  @Test
  public void extendsTestCase() throws ExecutionException {
    if (myJUnitVersion.equals("4.4")) {
      return; //runner doesn't exist
    }
    ProcessOutput output = doStartProcess();
    assertEmpty(output.err);
    assertFalse(ContainerUtil.exists(output.messages, m -> m instanceof TestFailed));
  }

  private ProcessOutput doStartProcess() throws ExecutionException {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    return doStartTestsProcess(configuration);
  }
}
