// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.JUnitParameterCollector.Parameter;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.junit.testFramework.MavenTestLib;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JUnitChooseParameterTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor DESCRIPTOR = new JUnitProjectDescriptor(LanguageLevel.HIGHEST, MavenTestLib.JUNIT5);

  private static final String PARAMETERIZED_METHOD = """
    import org.junit.jupiter.params.ParameterizedTest;
    import org.junit.jupiter.params.provider.ValueSource;
    public class MyTest {
      @ParameterizedTest
      @ValueSource(ints = {1, -3})
      public void para<caret>meterized(int i) {}
    }""";

  private static final String PARAMETERIZED_CLASS = """
    import org.junit.jupiter.params.ParameterizedClass;
    import org.junit.jupiter.params.ParameterizedTest;
    import org.junit.jupiter.params.Parameter;
    import org.junit.jupiter.params.provider.ValueSource;
    @ParameterizedClass
    @ValueSource(strings = {"radar", "level"})
    public class MyTest {
      @Parameter
      String candidate;
      @ParameterizedTest
      @ValueSource(ints = {1, -3})
      public void para<caret>meterized(int i) {}
    }""";

  /** One uniqueId of a parameter. Narrowing does not read the grammar of it, so any id will do. */
  private static final String FIRST_ID = "[class:MyTest]/[template]/[#1]";

  /** The collectors of the two launches the context produces for the configured source: one of the class, one of its method. */
  private JUnitParameterCollector myOfClass;
  private JUnitParameterCollector myOfMethod;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RegistryManager.getInstance().get("junit.choose.test.parameter").setValue(true, getTestRootDisposable());
  }

  public void testParameterizedTestAlwaysAsks() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.ValueSource;
      public class MyTest {
        @ParameterizedTest
        @ValueSource(strings = {"hello", "radar"})
        public void para<caret>meterized(String word) {}
      }""");
    assertConfigurationIsReused(caretMethod(file), false);
  }

  public void testParameterizedClassAlwaysAsks() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.params.ParameterizedClass;
      import org.junit.jupiter.params.Parameter;
      import org.junit.jupiter.params.provider.ValueSource;
      @ParameterizedClass
      @ValueSource(strings = {"hello", "radar"})
      public class MyTest {
        @Parameter
        String word;
        @Test
        public void test() {}
      }""");
    assertConfigurationIsReused(topLevelClass(file), false);
  }

  public void testPlainTestReusesItsConfiguration() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.Test;
      public class MyTest {
        @Test
        public void pla<caret>in() {}
      }""");
    assertConfigurationIsReused(caretMethod(file), true);
  }

  public void testPlainTestIsRunWithoutAsking() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.Test;
      public class MyTest {
        @Test
        public void pla<caret>in() {}
      }""");
    assertTrue("a test without parameters has to be run right away", isRunWithoutAsking(caretMethod(file)));
  }

  public void testParameterizedTestIsRunWithoutAskingWhenTheFeatureIsOff() {
    RegistryManager.getInstance().get("junit.choose.test.parameter").setValue(false, getTestRootDisposable());
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.ValueSource;
      public class MyTest {
        @ParameterizedTest
        @ValueSource(strings = {"hello", "radar"})
        public void para<caret>meterized(String word) {}
      }""");
    assertTrue(isRunWithoutAsking(caretMethod(file)));
  }

  /**
   * Whether a launch from the context runs the test at once, instead of asking which parameters to run first. The asking itself is not
   * asserted here, as that shows a popup; it is pinned from the other two sides instead — the gate has to let a test without parameters
   * through, and it has to let a parameterized one through while the feature is off.
   */
  private boolean isRunWithoutAsking(@NotNull PsiMember test) {
    ConfigurationContext context = createContext(test);
    RunConfigurationProducer<?> producer = RunConfigurationProducer.getInstance(TestInClassConfigurationProducer.class);
    ConfigurationFromContext fromContext = producer.createConfigurationFromContext(context);
    assertNotNull(fromContext);
    Ref<Boolean> run = Ref.create(false);
    producer.onFirstRun(fromContext, context, () -> run.set(true));
    return run.get();
  }

  public void testNothingIsOfferedWhenTheFeatureIsOff() {
    RegistryManager.getInstance().get("junit.choose.test.parameter").setValue(false, getTestRootDisposable());
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.ValueSource;
      public class MyTest {
        @ParameterizedTest
        @ValueSource(strings = {"hello", "radar"})
        public void para<caret>meterized(String word) {}
      }""");
    assertConfigurationIsReused(caretMethod(file), true);
  }

  public void testRepeatedTestReusesItsConfiguration() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.RepeatedTest;
      public class MyTest {
        @RepeatedTest(3)
        public void repe<caret>ated() {}
      }""");
    // repetitions are not parameters: there is nothing to tell them apart by
    assertConfigurationIsReused(caretMethod(file), true);
  }

  public void testTestFactoryAlwaysAsks() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.TestFactory;
      public class MyTest {
        @TestFactory
        public Object fact<caret>ory() { return null; }
      }""");
    assertConfigurationIsReused(caretMethod(file), false);
  }

  public void testPlainTestClassReusesItsConfiguration() {
    PsiFile file = myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.ValueSource;
      public class MyTest {
        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        public void parameterized(int i) {}
      }""");
    // running the class runs all of its tests anyway: there is no class level parameter to choose
    assertConfigurationIsReused(topLevelClass(file), true);
  }

  /** The one place the location format is written down: every test below builds its messages out of the derived location. */
  public void testLocationIsWhatTheRunnerReports() {
    configureCollectors(PARAMETERIZED_METHOD);
    assertEquals("java:suite://MyTest", myOfClass.locationHint());
    assertEquals("java:test://MyTest/parameterized", myOfMethod.locationHint());
  }

  public void testInvocationsOfMethodAreOffered() {
    configureCollectors(PARAMETERIZED_METHOD);
    List<Parameter> parameters = myOfMethod.parse(
      List.of(suiteStarted("[class:MyTest]", "0", "MyTest", myOfClass),
              suiteStarted("[class:MyTest]/[template]", "[class:MyTest]", "parameterized(int)", myOfMethod),
              testStarted("[class:MyTest]/[template]/[#1]", "[class:MyTest]/[template]", "[1] 1"),
              testStarted("[class:MyTest]/[template]/[#2]", "[class:MyTest]/[template]", "[2] -3")));
    assertEquals(List.of(new Parameter(List.of("[class:MyTest]/[template]/[#1]"), "[1] 1"),
                         new Parameter(List.of("[class:MyTest]/[template]/[#2]"), "[2] -3")), parameters);
  }

  public void testParameterSetsOfClassAreOffered() {
    configureCollectors(PARAMETERIZED_CLASS);
    List<Parameter> parameters = myOfClass.parse(classTemplateRun());
    assertEquals(List.of(new Parameter(List.of("[class-template:MyTest]/[class-template-invocation:#1]"), "[1] candidate = \"radar\""),
                         new Parameter(List.of("[class-template:MyTest]/[class-template-invocation:#2]"), "[2] candidate = \"level\"")),
                 parameters);
  }

  /**
   * A method of a parameterized class runs once per parameter set of that class, so its own parameters are reported that many times.
   * Each of them has to be offered once, and running it has to run it for every parameter set.
   */
  public void testParametersOfAMethodOfAParameterizedClassAreOfferedOnce() {
    configureCollectors(PARAMETERIZED_CLASS);
    List<Parameter> parameters = myOfMethod.parse(classTemplateRun());
    assertEquals(List.of(new Parameter(List.of("[class-template-invocation:#1]/[template]/[#1]",
                                               "[class-template-invocation:#2]/[template]/[#1]"), "[1] 1"),
                         new Parameter(List.of("[class-template-invocation:#1]/[template]/[#2]",
                                               "[class-template-invocation:#2]/[template]/[#2]"), "[2] -3")),
                 parameters);
  }

  /**
   * Two parameter sets of a parameterized class, each running the two parameters of the same method, as the runner reports a run of
   * that method: the tree is pruned to what runs, so the class itself has no node and the parameter sets hang under the root.
   */
  private @NotNull List<String> classTemplateRun() {
    List<String> messages = new ArrayList<>();
    for (String set : List.of("#1", "#2")) {
      String setId = "[class-template:MyTest]/[class-template-invocation:" + set + "]";
      String templateId = "[class-template-invocation:" + set + "]/[template]";
      messages.add(suiteStarted(setId, "0", "[" + set.substring(1) + "] candidate = \"" +
                                            ("#1".equals(set) ? "radar" : "level") + "\"", myOfClass));
      messages.add(suiteStarted(templateId, setId, "parameterized(int)", myOfMethod));
      messages.add(testStarted(templateId + "/[#1]", templateId, "[1] 1"));
      messages.add(testStarted(templateId + "/[#2]", templateId, "[2] -3"));
    }
    return messages;
  }

  public void testNothingToOfferForASingleInvocation() {
    configureCollectors(PARAMETERIZED_METHOD);
    assertEmpty(myOfClass.parse(
      List.of(suiteStarted("[class:MyTest]", "0", "MyTest", myOfClass),
              suiteStarted("[class:MyTest]/[template]", "[class:MyTest]", "parameterized(int)", myOfMethod))));
  }

  public void testOutputThatIsNotATestTreeIsIgnored() {
    configureCollectors(PARAMETERIZED_METHOD);
    assertEmpty(myOfMethod.parse(
      List.of("just some output of the test",
              "##teamcity[testFinished name='hello' nodeId='child' parentNodeId='root']")));
  }

  /** Running one parameter runs that parameter alone, and names the launch after it. */
  public void testNarrowingRunsTheChosenParameterAlone() {
    PsiFile file = myFixture.configureByText("MyTest.java", PARAMETERIZED_METHOD);
    RunnerAndConfigurationSettings launch = launchOf(caretMethod(file));
    JUnitConfiguration configuration = (JUnitConfiguration)launch.getConfiguration();

    new JUnitSingleParameter(launch).updateConfiguration(new Parameter(List.of(FIRST_ID), "[1] 1"));

    assertEquals(JUnitConfiguration.TEST_UNIQUE_ID, configuration.getPersistentData().TEST_OBJECT);
    assertOrderedEquals(configuration.getPersistentData().getUniqueIds(), FIRST_ID);
    assertEquals("named after the test and the parameter", "MyTest.parameterized.[1] 1", configuration.getName());
  }

  /**
   * A configuration the user saved for that very parameter answers for how to run it, as the user set it up on purpose. The launch
   * keeps its own name even then: it comes from the context, and not from that saved configuration.
   */
  public void testNarrowingTakesTheSavedSettingsAndKeepsItsOwnName() {
    PsiFile file = myFixture.configureByText("MyTest.java", PARAMETERIZED_METHOD);
    RunnerAndConfigurationSettings launch = launchOf(caretMethod(file));
    JUnitConfiguration configuration = (JUnitConfiguration)launch.getConfiguration();
    RunnerAndConfigurationSettings saved = saveParameterConfiguration("-Dsaved=true", FIRST_ID);
    try {
      new JUnitSingleParameter(launch).updateConfiguration(new Parameter(List.of(FIRST_ID), "[1] 1"));

      assertEquals("the settings the user saved for the parameter", "-Dsaved=true", configuration.getVMParameters());
      assertOrderedEquals(configuration.getPersistentData().getUniqueIds(), FIRST_ID);
      assertEquals("named after the test and the parameter", "MyTest.parameterized.[1] 1", configuration.getName());
    }
    finally {
      RunManager.getInstance(getProject()).removeConfiguration(saved);
    }
  }

  /** A configuration the user saved to run exactly {@code ids}, registered in the RunManager as a saved one is. */
  private @NotNull RunnerAndConfigurationSettings saveParameterConfiguration(@NotNull String vmParameters, String @NotNull ... ids) {
    RunManager runManager = RunManager.getInstance(getProject());
    RunnerAndConfigurationSettings saved =
      runManager.createConfiguration("saved", JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    JUnitConfiguration configuration = (JUnitConfiguration)saved.getConfiguration();
    configuration.beUniqueIdConfiguration(ids);
    configuration.setModule(getModule());
    configuration.setVMParameters(vmParameters);
    runManager.addConfiguration(saved);
    return saved;
  }

  /**
   * One collector answers for one test, so the two are built from the two configurations a launch from the context produces: the
   * location they look for is the one the runner reports for that very launch.
   */
  private void configureCollectors(@NotNull String text) {
    PsiFile file = myFixture.configureByText("MyTest.java", text);
    myOfClass = collectorOf(topLevelClass(file));
    myOfMethod = collectorOf(caretMethod(file));
  }

  private @NotNull JUnitParameterCollector collectorOf(@NotNull PsiMember test) {
    String testName = test.getName();
    assertNotNull(testName);
    return new JUnitParameterCollector(launchOf(test), testName);
  }

  /** The launch a run from the context of {@code test} produces. It is not registered in the RunManager, as no launch from a context is. */
  private @NotNull RunnerAndConfigurationSettings launchOf(@NotNull PsiMember test) {
    RunnerAndConfigurationSettings settings = createContext(test).getConfiguration();
    assertNotNull(settings);
    assertInstanceOf(settings.getConfiguration(), JUnitConfiguration.class);
    return settings;
  }

  private @NotNull String testStarted(@NotNull String id, @NotNull String parentId, @NotNull String name) {
    return serviceMessage("testStarted", id, parentId, name, myOfMethod.locationHint());
  }

  private static @NotNull String suiteStarted(@NotNull String id,
                                              @NotNull String parentId,
                                              @NotNull String name,
                                              @NotNull JUnitParameterCollector of) {
    return serviceMessage("testSuiteStarted", id, parentId, name, of.locationHint());
  }

  private static @NotNull String serviceMessage(@NotNull String type,
                                                @NotNull String id,
                                                @NotNull String parentId,
                                                @NotNull String name,
                                                @NotNull String hint) {
    return "##teamcity[" + type + " name='" + escape(name) + "' nodeId='" + escape(id) + "' parentNodeId='" + escape(parentId) +
           "' locationHint='" + escape(hint) + "']";
  }

  /** As the runner escapes the values it reports, see {@code MapSerializerUtil}. */
  private static @NotNull String escape(@NotNull String value) {
    return value.replace("|", "||").replace("'", "|'").replace("[", "|[").replace("]", "|]");
  }

  /**
   * Whether a launch from the context reuses the configuration of the whole test, or asks which parameters to run instead.
   * Neither a throwaway temporary configuration nor one saved on purpose may answer for a parameterized test: the choice made once
   * would silently apply to every launch after it.
   */
  private void assertConfigurationIsReused(@NotNull PsiMember test, boolean reused) {
    RunManager runManager = RunManager.getInstance(getProject());
    RunnerAndConfigurationSettings settings = createContext(test).getConfiguration();
    assertNotNull(settings);
    assertInstanceOf(settings.getConfiguration(), JUnitConfiguration.class);
    try {
      runManager.setTemporaryConfiguration(settings);
      assertEquals("temporary configuration", reused ? settings : null, createContext(test).findExisting());

      runManager.makeStable(settings);
      assertEquals("saved configuration", reused ? settings : null, createContext(test).findExisting());
    }
    finally {
      runManager.removeConfiguration(settings);
    }
  }

  private @NotNull ConfigurationContext createContext(@NotNull PsiMember test) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.EDITOR, myFixture.getEditor())
      .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(test))
      .add(Location.DATA_KEY, PsiLocation.fromPsiElement(test))
      .build();
    return ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
  }

  private @NotNull PsiMethod caretMethod(@NotNull PsiFile file) {
    assertNotNull(file);
    PsiMethod method = (PsiMethod)myFixture.getElementAtCaret();
    assertNotNull(method);
    return method;
  }

  private static @NotNull PsiClass topLevelClass(@NotNull PsiFile file) {
    PsiClass testClass = ((PsiClassOwner)file).getClasses()[0];
    assertNotNull(testClass);
    return testClass;
  }
}
