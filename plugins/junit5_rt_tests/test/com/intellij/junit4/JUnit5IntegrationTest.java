/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.junit4;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.idea.Bombed;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestIgnored;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class JUnit5IntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/mixed45Project");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
     ModuleRootModificationUtil.updateModel(myModule, 
                                           model -> model.addContentEntry(getTestContentRoot()).addSourceFolder(getTestContentRoot() + "/test1", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0"), repoManager);
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "4.12"), repoManager);
  }

  public void testRunPackage() throws Exception {
    RunConfiguration configuration = createRunPackageConfiguration("mixed");
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertSize(4, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
  }

  public void testSelectedMethods() throws Exception {
    final IdeaTestApplication testApplication = IdeaTestApplication.getInstance();
    try {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      PsiElement[] elements = new PsiElement[]{
        psiFacade.findClass("mixed.v5.MyTest5", scope).getMethods()[0],
        psiFacade.findClass("mixed.v4.MyTest4", scope).getMethods()[0]
      };
      testApplication.setDataProvider(new TestDataProvider(myProject) {
        @Override
        public Object getData(@NotNull @NonNls String dataId) {
          if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
            return elements;
          }
          return super.getData(dataId);
        }
      });
      MapDataContext dataContext = new MapDataContext();

      dataContext.put(LangDataKeys.PSI_ELEMENT_ARRAY, elements);
      dataContext.put(CommonDataKeys.PROJECT, myProject);
      ConfigurationContext fromContext = ConfigurationContext.getFromContext(dataContext);
      assertNotNull(fromContext);

      RunConfiguration configuration = fromContext.getConfiguration().getConfiguration();
      assertNotNull(configuration);

      ProcessOutput processOutput = doStartTestsProcess(configuration);

      assertTrue(processOutput.sys.toString().contains("-junit5"));
      //assertEmpty(err); // commented due unavoidable messages from JUnit engine: WARNING: Method 'public void mixed.v4.MyTest4.singleMethodTest()' could not be resolved
      assertEmpty(processOutput.out);
      assertSize(2, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
      TestFailed firstFailure = (TestFailed)ContainerUtil.find(processOutput.messages, TestFailed.class::isInstance);
      assertNotNull(firstFailure);
      String id = firstFailure.getAttributes().get("id");
      assertNotNull(id);
      assertTrue("First failure: " + id, id.contains("v5"));
    }
    finally {
      testApplication.setDataProvider(null);
    }
  }

  public void testPatternConfiguration() throws Exception {
    final IdeaTestApplication testApplication = IdeaTestApplication.getInstance();
    try {
      JUnitConfiguration configuration = new JUnitConfiguration("pattern", getProject());
      JUnitConfiguration.Data data = configuration.getPersistentData();
      data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
      LinkedHashSet<String> pattern = new LinkedHashSet<>();
      pattern.add(".*MyTest.*");
      data.setPatterns(pattern);
      data.setScope(TestSearchScope.WHOLE_PROJECT);

      ProcessOutput processOutput = doStartTestsProcess(configuration);

      assertTrue(processOutput.sys.toString().contains("-junit5"));
      assertEmpty(processOutput.out);
      assertSize(4, ContainerUtil.filter(processOutput.messages, TestStarted.class::isInstance));
      assertSize(4, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
    }
    finally {
      testApplication.setDataProvider(null);
    }
  }

  public void testJUnit4MethodRunWithJUnit4Runner() throws Exception {
    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("mixed.v4.MyTest4", GlobalSearchScope.projectScope(myProject));
    assertNotNull(testClass);
    RunConfiguration configuration = createConfiguration(testClass.getMethods()[0]);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String systemOutput = processOutput.sys.toString(); //command line

    //check running with junit 4
    assertTrue(systemOutput.contains("-junit4"));
    assertFalse(systemOutput.contains("-junit5"));

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertSize(1, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
  }


  public void testRunClass() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("mixed.v5.MyTest5", GlobalSearchScope.projectScope(myProject));
    RunConfiguration configuration = createConfiguration(aClass);

    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String systemOutput = processOutput.sys.toString(); //command line

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertSize(1, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
    assertTrue(systemOutput.contains("-junit5"));
  }

  public void testRunClassRepeatedTwice() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("mixed.v5.MyTest5", GlobalSearchScope.projectScope(myProject));
    RunConfiguration configuration = createConfiguration(aClass);
    ((JUnitConfiguration)configuration).setRepeatMode(RepeatCount.N);
    ((JUnitConfiguration)configuration).setRepeatCount(2);
    

    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String systemOutput = processOutput.sys.toString(); //command line

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertSize(2, ContainerUtil.filter(processOutput.messages, TestFailed.class::isInstance));
    assertTrue(systemOutput.contains("-junit5"));
  }

  public void testIgnoreDisabledTestClass() throws Exception {
    RunConfiguration configuration = createRunPackageConfiguration("disabled");
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    List<TestIgnored> ignoredTests = processOutput.messages.stream()
      .filter(TestIgnored.class::isInstance)
      .map(TestIgnored.class::cast)
      .filter(test -> test.getIgnoreComment().equals("Class disabled"))
      .collect(Collectors.toList());
    assertSize(2, ignoredTests); // each method from class reported
  }

  public void testDirectory() throws Exception {
    RunConfiguration configuration = createRunDirectoryConfiguration("mixed.dir5");
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertEquals(1, processOutput.messages.stream().filter(TestStarted.class::isInstance).count());
    
  }

  @Bombed(month = Calendar.AUGUST, day = 31, user = "Timur Yuldashev", description = "IDEA-174534")
  public void testRunSpecificDisabledTestClass() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("disabled.DisabledClass", GlobalSearchScope.projectScope(myProject));
    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    // not disabled method should be executed, assuming only start/finish events
    assertSize(2, processOutput.messages.stream()
      .filter(BaseTestMessage.class::isInstance)
      .map(BaseTestMessage.class::cast)
      .filter(message -> message.getTestName().equals("testShouldBeExecuted()"))
      .collect(Collectors.toList()));
    // disabled test method should be Ignored
    List<TestIgnored> ignoredTests = processOutput.messages.stream()
      .filter(TestIgnored.class::isInstance)
      .map(TestIgnored.class::cast)
      .collect(Collectors.toList());
    assertSize(1, ignoredTests);
    assertEquals("testDisabledMethod()", ignoredTests.get(0).getTestName());
  }

  public void testIgnoreDisabledTestMethod() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("disabled.DisabledMethod", GlobalSearchScope.projectScope(myProject));
    RunConfiguration configuration = createConfiguration(aClass);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    List<TestIgnored> ignoredTests = processOutput.messages.stream()
      .filter(TestIgnored.class::isInstance)
      .map(TestIgnored.class::cast)
      .collect(Collectors.toList());
    assertSize(1, ignoredTests);
    assertEquals("testDisabledMethod()", ignoredTests.get(0).getTestName());
    assertEquals("Method disabled", ignoredTests.get(0).getIgnoreComment());
  }

  public void testRunSpecificDisabledMethod() throws Exception {
    PsiMethod aMethod = JavaPsiFacade.getInstance(myProject)
      .findClass("disabled.DisabledMethod", GlobalSearchScope.projectScope(myProject))
      .findMethodsByName("testDisabledMethod", false)[0];
    RunConfiguration configuration = createConfiguration(aMethod);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertNoIgnored(processOutput);

    //assuming only suiteTreeNode/start/finish events
    assertSize(3,
               ContainerUtil.filter(processOutput.messages, m -> m.getAttributes().getOrDefault("name", "").equals("testDisabledMethod()")));
  }

  public void testRunSpecificDisabledIfMethod() throws Exception {
    PsiMethod aMethod = JavaPsiFacade.getInstance(myProject)
      .findClass("disabled.DisabledMethodIf", GlobalSearchScope.projectScope(myProject))
      .findMethodsByName("testDisabledMethod", false)[0];
    RunConfiguration configuration = createConfiguration(aMethod);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertNoIgnored(processOutput);

    //assuming only suiteTreeNode/start/finish events
    assertSize(3, ContainerUtil
      .filter(processOutput.messages, m -> m.getAttributes().getOrDefault("name", "").equals("testDisabledMethod()")));
  }

  public void testRunSpecificDisabledMethodByCondition() throws Exception {
    PsiMethod aMethod = JavaPsiFacade.getInstance(myProject)
      .findClass("disabled.DisabledConditionalMethod", GlobalSearchScope.projectScope(myProject))
      .findMethodsByName("testDisabledMethod", false)[0];
    RunConfiguration configuration = createConfiguration(aMethod);

    ProcessOutput processOutput = doStartTestsProcess(configuration);

    assertNoIgnored(processOutput);

    //assuming only suiteTreeNode/start/failed(no String to inject)/finish events
    assertSize(4, ContainerUtil
      .filter(processOutput.messages, m -> m.getAttributes().getOrDefault("name", "").equals("testDisabledMethod(String)")));
  }

  private static void assertNoIgnored(ProcessOutput processOutput) {
    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);
    assertSize(0, processOutput.messages.stream().filter(TestIgnored.class::isInstance).map(TestIgnored.class::cast)
      .collect(Collectors.toList()));
  }

  @NotNull
  public RunConfiguration createRunPackageConfiguration(final String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
    assertNotNull(aPackage);
    RunConfiguration configuration = createConfiguration(aPackage);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }

  @NotNull
  public RunConfiguration createRunDirectoryConfiguration(final String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
    assertNotNull(aPackage);
    PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.moduleTestsWithDependentsScope(myModule));
    assertTrue(directories.length > 0);
    JUnitConfiguration configuration = new JUnitConfiguration("dir", myProject);
    JUnitConfiguration.Data data = configuration.getPersistentData();
    data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY;
    data.setDirName(directories[0].getVirtualFile().getPath());
    configuration.setModule(ModuleUtilCore.findModuleForPsiElement(directories[0]));
    return configuration;
  }
}
