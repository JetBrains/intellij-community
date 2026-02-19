// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import jetbrains.buildServer.messages.serviceMessages.TestFinished;
import jetbrains.buildServer.messages.serviceMessages.TestIgnored;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class JUnit5IntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/junit5Project");
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_17;
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(getTestContentRoot())
      .addSourceFolder(getTestContentRoot() + "/test", true));
    final ArtifactRepositoryManager repoManager = getRepoManager();

    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.13.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-params", "5.13.0"), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", "1.13.1"),
                 repoManager);
  }

  public void testConditionalExecution() throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("conditional.ConditionalTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    RunConfiguration configuration = createConfiguration(aClass);
    assertNotNull("Run configuration is null", configuration);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTestsStarted(output, "enabled()", "disabled()");
    assertTestsFinished(output, "enabled()", "disabled()");
    assertTestsIgnored(output, "disabled()");
  }

  public void testInterfaceDynamicTestsDemo() throws Exception {
    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("dynamicTests.A", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    List<ServiceMessage> messages = output.messages;

    var testNames = messages.stream().filter(TestStarted.class::isInstance).map(m -> m.getAttributes().get("name")).toList();

    assertEquals(7, testNames.size());
    assertEquals(7, messages.stream().filter(TestFinished.class::isInstance).count());
    assertTestsFailed(output, "false", "false");
    assertEquals(4, testNames.stream().filter(name -> name.contains("true")).count());
  }

  public void testMetaAnnotationDiscoveryAndExecution() throws Exception {
    PsiClass testClass =
      JavaPsiFacade.getInstance(myProject).findClass("metaAnnotation.MetaAnnotationTest", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    List<ServiceMessage> messages = output.messages;
    assertEquals(7, messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());

    assertTestsStarted(output, "integrationTest()", "Retry 1/3", "Retry 2/3", "Retry 3/3", "Retry 1/3", "Retry 2/3", "Retry 3/3");
    assertTestSuitesStarted(output, "retryTest()", "combinedTest()");
  }

  public void testMetaAnnotationSuiteDiscoveryAndExecution() throws Exception {
    PsiClass testClass =
      JavaPsiFacade.getInstance(myProject).findClass("metaAnnotation.IntegrationTestSuite", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    List<ServiceMessage> messages = output.messages;
    assertEquals(7, messages.stream().filter(TestStarted.class::isInstance).count());
    assertEquals(7, messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());

    assertTestsStarted(output, "integrationTest()", "Retry 1/3", "Retry 2/3", "Retry 3/3", "integrationTest()", "nestedIntegrationTest()",
                       "testWithoutIntegrationTagTest()");
    assertTestSuitesStarted(output, "Meta-annotation demonstration", "NestedIntegrationTests", "combinedTest()");
  }

  public void testSuiteExecution() throws Exception {
    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("testSuite.TestSuite", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test suite class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    assertTestsStarted(output, "executed()", "test1()", "test1()", "test2()");
    assertTestsFinished(output, "executed()", "test1()", "test1()", "test2()");
    assertTestsFailed(output, "test2()");

    assertTestSuitesStarted(output, "RecordSuiteTest", "SecondSuiteTest", "SuiteTest");
  }

  public void testDisabledTests() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("various.DisabledTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.out);
    assertEmpty(output.err);
    List<ServiceMessage> messages = output.messages;

    assertTestsStarted(output, "testWillBeExecuted()", "testWillBeSkipped()", "testInNestedClassWillBeSkipped()");
    assertTestsFinished(output, "testWillBeExecuted()", "testWillBeSkipped()", "testInNestedClassWillBeSkipped()");
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());
    assertTestsIgnored(output, "testWillBeSkipped()", "testInNestedClassWillBeSkipped()");
  }

  public void testDisplayNames() throws Exception {
    PsiClass fragmentClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.DisplayNameGenerators", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", fragmentClass);

    RunConfiguration fragmentConfig = createConfiguration(fragmentClass);
    ProcessOutput fragmentOutput = doStartTestsProcess(fragmentConfig);

    assertEmpty(fragmentOutput.err);
    assertTrue("Should use JUnit 5", fragmentOutput.sys.toString().contains("-junit5"));

    assertEquals(8, fragmentOutput.messages.stream().filter(TestStarted.class::isInstance).count());
    assertEquals(0, fragmentOutput.messages.stream().filter(TestFailed.class::isInstance).count());
    assertEquals(8, fragmentOutput.messages.stream().filter(TestFinished.class::isInstance).count());

    assertTestSuitesStarted(fragmentOutput, "Fragment1", "Fragment1, fragment3", "Replace underscores in class",
                            "parameterized test (int)");
    assertTestsStarted(fragmentOutput, "1", "2", "3", "Fragment1, fragment2", "Display_name_with_underscores",
                       "replace underscores in method", "Parameterized test name works 1", "Parameterized test name works 2");
  }

  public void testOrderedNestedClassesExecution() throws Exception {
    PsiClass testClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.OrderedNestedTestClassesDemo", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    assertTestsOrder(output, "test0()", "test1()", "test2()");

    assertEquals(0, output.messages.stream().filter(TestFailed.class::isInstance).count());
    assertEquals(3, output.messages.stream().filter(TestFinished.class::isInstance).count());
  }

  public void testMethodOrderExecution() throws Exception {
    PsiClass testClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.OrderedTestsDemo", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    assertTestsOrder(output, "test1()", "test2()", "test4()");

    assertEquals(3, output.messages.stream().filter(TestFinished.class::isInstance).count());
    assertEquals(0, output.messages.stream().filter(TestFailed.class::isInstance).count());
  }

  public void testParameterizedMethod() throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.ParameterizedTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);
    RunConfiguration configuration = createConfiguration(aClass);

    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String systemOutput = processOutput.sys.toString(); //command line

    assertEmpty(processOutput.out);
    assertEmpty(processOutput.err);

    assertEquals(4, processOutput.messages.stream().filter(TestStarted.class::isInstance).count());
    assertEquals(4, processOutput.messages.stream().filter(TestFinished.class::isInstance).count());
    assertTrue(systemOutput.contains("-junit5"));

    assertTestsFailed(processOutput, "49 + 51 = 101");
  }

  public void testRepeatedTests() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("various.RepeatedTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTestsStarted(output, "repetition 1 of 3", "repetition 2 of 3", "repetition 3 of 3");
  }

  public void testFactoryTests() throws Exception {
    PsiClass testClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.TestFactoryTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", testClass);

    RunConfiguration configuration = createConfiguration(testClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    assertTrue("Should use JUnit 5", output.sys.toString().contains("-junit5"));

    List<ServiceMessage> messages = output.messages;

    assertEquals(8, messages.stream().filter(TestStarted.class::isInstance).count());

    assertTestsStarted(output, "test 1", "test 1", "test 2", "test 2", "test 3", "test 3", "1st dynamic test", "2nd dynamic test");
    assertTestSuitesStarted(output, "Container A", "Container B", "container level 2", "container level 2", "dynamicTestsWithContainers()",
                            "testsFromCollection()");
  }

  public void testTemplateTests() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("various.TestTemplateDemo", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertEmpty(output.err);
    List<ServiceMessage> messages = output.messages;
    assertEquals(0, messages.stream().filter(TestFailed.class::isInstance).count());
    assertTestsStarted(output, "first test 1", "second test 2");
    assertTestsFinished(output, "first test 1", "second test 2");
  }

  public void testUnhandledException() throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("various.UncaughtExceptionHandlingDemo", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertTestsStarted(output, "passes()", "failsDueToUncaughtException()");
    assertTestsFinished(output, "passes()", "failsDueToUncaughtException()");
    assertTestsFailed(output, "failsDueToUncaughtException()");
    assertEmpty(output.err);
  }

  public void testCheckClasspath() throws ExecutionException {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("checkClasspath.CheckerTest", GlobalSearchScope.projectScope(myProject));
    assertNotNull(aClass);

    RunConfiguration configuration = createConfiguration(aClass);
    ProcessOutput output = doStartTestsProcess(configuration);

    assertTestsStarted(output, "test()");
    assertTestsFinished(output, "test()");
    assertTestsFailed(output);
    assertEmpty(output.err);
  }

  private static void assertTestsStatus(ProcessOutput output, Predicate<ServiceMessage> predicate, String... testNames) {
    var actualNames = output.messages.stream().filter(predicate)
      .map(m -> m.getAttributes().get("name"))
      .collect(Collectors.toSet());
    assertEquals(ContainerUtil.newHashSet(testNames), actualNames);
  }

  private static void assertTestsStarted(ProcessOutput output, String... testNames) {
    assertTestsStatus(output, TestStarted.class::isInstance, testNames);
  }

  private static void assertTestsFinished(ProcessOutput output, String... testNames) {
    assertTestsStatus(output, TestFinished.class::isInstance, testNames);
  }

  private static void assertTestsFailed(ProcessOutput output, String... testNames) {
    assertTestsStatus(output, TestFailed.class::isInstance, testNames);
  }

  private static void assertTestsIgnored(ProcessOutput output, String... testNames) {
    assertTestsStatus(output, TestIgnored.class::isInstance, testNames);
  }

  private static void assertTestSuitesStarted(ProcessOutput output, String... suiteNames) {
    assertTestsStatus(output, TestSuiteStarted.class::isInstance, suiteNames);
  }

  private static void assertTestsOrder(ProcessOutput output, String... expectedOrder) {
    List<String> executionOrder = output.messages.stream()
      .filter(TestStarted.class::isInstance)
      .map(m -> m.getAttributes().get("name")).toList();

    assertEquals("execution order is wrong", List.of(expectedOrder), executionOrder);
  }
}