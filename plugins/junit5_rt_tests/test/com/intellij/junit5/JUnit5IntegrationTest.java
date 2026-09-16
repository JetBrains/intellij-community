// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitParameterCollector;
import com.intellij.execution.junit.JUnitParameterCollector.Parameter;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.junit.JUnitStarter;
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

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.junit5.JUnitRtConstants.JUNIT5_PLATFORM_VERSION;
import static com.intellij.junit5.JUnitRtConstants.JUNIT5_VERSION;

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

    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", JUNIT5_VERSION), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-params", JUNIT5_VERSION), repoManager);
    addMavenLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.junit.platform", "junit-platform-suite-api", JUNIT5_PLATFORM_VERSION),
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

  public void testCollectParameterizedDoesNotExecuteBodies() throws Exception {
    assertCollectReportsInvocationsWithoutRunningBodies("parameterized", List.of("[1] -1", "[2] 7", "[3] 0"));
  }

  public void testCollectTestFactoryDoesNotExecuteBodies() throws Exception {
    assertCollectReportsInvocationsWithoutRunningBodies("factory", List.of("Test 1", "Test 2", "Test 3"));
  }

  /**
   * A method of a {@code @ParameterizedClass} runs once per parameter set of that class, so the runner reports its parameters that many
   * times. The IDE has to offer each of them once, running it for every parameter set, and has to offer the sets themselves for the class.
   */
  public void testCollectParametersOfMethodOfParameterizedClass() throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("collect.CollectClassParametersTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", aClass);
    RunnerAndConfigurationSettings ofMethod = createContext(aClass.findMethodsByName("parameterized", false)[0]).getConfiguration();
    RunnerAndConfigurationSettings ofClass = createContext(aClass).getConfiguration();
    assertNotNull("Run configuration of the method is null", ofMethod);
    assertNotNull("Run configuration of the class is null", ofClass);

    File marker = FileUtil.createTempFile("junit-collect-marker", ".txt", true);
    try {
      ProcessOutput output = doStartTestsProcess(ofMethod.getConfiguration(), parameters -> {
        parameters.getVMParametersList().addProperty("junit.jupiter.extensions.autodetection.enabled", "true");
        parameters.getVMParametersList().addProperty(JUnitStarter.DRY_RUN_PROPERTY, "true");
        parameters.getVMParametersList().addProperty("idea.junit.test.marker.file", marker.getAbsolutePath());
      });
      assertEmpty(output.err);
      assertEquals("test bodies must NOT run during the collect pass", "", FileUtil.loadFile(marker).trim());

      List<String> messages = ContainerUtil.map(output.messages, ServiceMessage::asString);
      // one collector answers for one test, so the two questions over the same messages take two collectors
      List<Parameter> parametersOfMethod = new JUnitParameterCollector(ofMethod, "parameterized").parse(messages);
      assertEquals(List.of("[1] 1", "[2] -3"), ContainerUtil.map(parametersOfMethod, Parameter::displayName));
      for (Parameter parameter : parametersOfMethod) {
        assertEquals("run for every parameter set of the class: " + parameter, 2, parameter.ids().size());
      }

      List<Parameter> parametersOfClass =
        new JUnitParameterCollector(ofClass, "CollectClassParametersTests").parse(messages);
      // exactly as jupiter presents the parameter sets of a class in the pinned version, see JUnitRtConstants
      assertEquals(List.of("[1] candidate=radar", "[2] candidate=level"),
                   ContainerUtil.map(parametersOfClass, Parameter::displayName));
    }
    finally {
      FileUtil.delete(marker);
    }
  }

  /**
   * A test of a parameterized class that has no parameters of its own still runs once per parameter set of that class. A dry run must
   * not run its body either: the fork is started to read the tree, and a test body may do anything.
   */
  public void testDryRunOfAParameterizedClassRunsNoBody() throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("collect.CollectClassParametersTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", aClass);
    RunnerAndConfigurationSettings ofClass = createContext(aClass).getConfiguration();
    assertNotNull("Run configuration of the class is null", ofClass);

    File marker = FileUtil.createTempFile("junit-collect-marker", ".txt", true);
    try {
      ProcessOutput output = doStartTestsProcess(ofClass.getConfiguration(), parameters -> {
        parameters.getVMParametersList().addProperty("junit.jupiter.extensions.autodetection.enabled", "true");
        parameters.getVMParametersList().addProperty(JUnitStarter.DRY_RUN_PROPERTY, "true");
        parameters.getVMParametersList().addProperty("idea.junit.test.marker.file", marker.getAbsolutePath());
      });
      assertEmpty(output.err);
      assertEquals("no test body may run during a dry run", "", FileUtil.loadFile(marker).trim());

      List<Parameter> parametersOfClass = new JUnitParameterCollector(ofClass, "CollectClassParametersTests")
        .parse(ContainerUtil.map(output.messages, ServiceMessage::asString));
      assertEquals("the parameter sets the IDE offers for the class", List.of("[1] candidate=radar", "[2] candidate=level"),
                   ContainerUtil.map(parametersOfClass, Parameter::displayName));
    }
    finally {
      FileUtil.delete(marker);
    }
  }

  /**
   * Runs the given data-driven method of {@code collect.CollectParametersTests} in "collect" mode (the flags the IDE
   * injects for the "run a single parameter" feature) and verifies both halves of the contract: every invocation is
   * reported, but no test body runs — the latter proven by the test bodies appending to a marker file that must stay empty.
   */
  private void assertCollectReportsInvocationsWithoutRunningBodies(String methodName, List<String> params) throws Exception {
    PsiClass aClass =
      JavaPsiFacade.getInstance(myProject).findClass("collect.CollectParametersTests", GlobalSearchScope.projectScope(myProject));
    assertNotNull("Test class not found", aClass);
    PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
    RunnerAndConfigurationSettings settings = createContext(method).getConfiguration();
    assertNotNull("Run configuration is null", settings);

    File marker = FileUtil.createTempFile("junit-collect-marker", ".txt", true);
    try {
      ProcessOutput output = doStartTestsProcess(settings.getConfiguration(), parameters -> {
        parameters.getVMParametersList().addProperty("junit.jupiter.extensions.autodetection.enabled", "true");
        parameters.getVMParametersList().addProperty(JUnitStarter.DRY_RUN_PROPERTY, "true");
        parameters.getVMParametersList().addProperty("idea.junit.test.marker.file", marker.getAbsolutePath());
      });

      assertEmpty(output.err);
      List<ServiceMessage> started = output.messages.stream()
        .filter(m -> m instanceof TestStarted || m instanceof TestSuiteStarted)
        .toList();
      List<String> res = started.stream().filter(TestStarted.class::isInstance).map(m -> m.getAttributes().get("name")).toList();
      assertEquals("all invocations should be reported during collect", params, res);
      assertEquals("test bodies must NOT run during the collect pass", "", FileUtil.loadFile(marker).trim());

      // the IDE reads the collected tree out of these attributes, see JUnitParameterCollector
      for (ServiceMessage message : started) {
        assertNotNull(message.asString(), message.getAttributes().get("nodeId"));
        assertNotNull(message.asString(), message.getAttributes().get("parentNodeId"));
      }
      List<Parameter> collected = new JUnitParameterCollector(settings, methodName)
        .parse(ContainerUtil.map(output.messages, ServiceMessage::asString));
      assertEquals("the parameters the IDE offers", params, ContainerUtil.map(collected, Parameter::displayName));
    }
    finally {
      FileUtil.delete(marker);
    }
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