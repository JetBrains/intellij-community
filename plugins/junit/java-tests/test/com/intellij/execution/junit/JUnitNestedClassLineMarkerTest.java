// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.junit.testFramework.MavenTestLib;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JUnitNestedClassLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(new JUnitProjectDescriptor(LanguageLevel.HIGHEST, MavenTestLib.JUNIT5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private final Set<RunnerAndConfigurationSettings> myTempSettings = new HashSet<>();

  @AfterEach
  void tearDown() {
    RunManager runManager = RunManager.getInstance(codeInsightFixture.get().getProject());
    for (RunnerAndConfigurationSettings setting : myTempSettings) {
      runManager.removeConfiguration(setting);
    }
  }

  @Test
  public void testNestedClassInAbstractOuter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class <caret>NestedTests {
                                       @Test void myTest() {}    }
                                }
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'TemplateTest$NestedTests'");
    assertEquals("ConcreteTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_CLASS, data.TEST_OBJECT);
    assertEquals("ConcreteTest$NestedTests", data.MAIN_CLASS_NAME);
  }

  @Test
  public void testAbstractOuterWithSingleInheritor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                   @Test void myTe<caret>st() {}}
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("ConcreteTest", data.MAIN_CLASS_NAME);
  }

  @Test
  public void testAbstractOuterWithSingleInheritorInNested() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest {
                                  @Test void my<caret>Test() {}
                                  static class ConcreteTest extends TemplateTest {}
                                }
                                """
    );
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$ConcreteTest", data.MAIN_CLASS_NAME);
  }

  @Test
  public void testMethodInStaticNestedInheritorClassInAbstractOuter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    static class NestedTests extends TemplateTest {
                                       @Test <caret> void myTest() {}
                                    }
                                    static class NestedTests1 extends TemplateTest {
                                       @Test void myTest() {}
                                    }
                                }
                                """);
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  @Test
  public void testMethodInNestedClassInAbstractOuter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class NestedTests {
                                       @Test <caret> void myTest() {}    }
                                }
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("ConcreteTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  @Test
  public void testMethodInStaticClassInAbstractOuter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Test void markerTest() {}
                                    static class NestedTests {
                                       @Test <caret> void myTest() {}    }
                                }
                                class ConcreteTest1 extends TemplateTest { }
                                class ConcreteTest2 extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  @Test
  public void testMethodInNestedInheritorClassInAbstractOuter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class NestedTests extends TemplateTest {
                                       @Test <caret> void myTest() {}    }
                                    @Nested
                                    class NestedTests1 extends TemplateTest {
                                       @Test void myTest() {}    }
                                }
                                """);
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  @NotNull
  private RunConfiguration startConfigurationFromGutter(String expectedRunTitle) {
    return EdtTestUtil.runInEdtAndGet(() -> doStartConfigurationFromGutter(expectedRunTitle));
  }

  private @NotNull RunConfiguration doStartConfigurationFromGutter(String expectedRunTitle) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    ActionGroup group = mark.getPopupMenuActions();
    assertNotNull(group);
    PresentationFactory factory = new PresentationFactory();
    List<AnAction> list = ContainerUtil.findAll(Utils.expandActionGroup(
      group, factory, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN, ActionUiKind.NONE), action -> {
      String text = factory.getPresentation(action).getText();
      return text != null && text.startsWith("Run '") && text.endsWith("'");
    });
    assertEquals(1, list.size(), list.toString());
    assertEquals(expectedRunTitle, factory.getPresentation(list.get(0)).getText());
    myFixture.testAction(list.get(0));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(myFixture.getProject()).getSelectedConfiguration();
    myTempSettings.add(selectedConfiguration);
    RunConfiguration configuration = selectedConfiguration.getConfiguration();
    assertInstanceOf(JUnitConfiguration.class, configuration);
    return configuration;
  }
}
