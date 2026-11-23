// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.java.codeInsight.navigation.MockGradleRunConfiguration;
import com.intellij.java.codeInsight.navigation.TempRunConfigurations;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestRunLineMarkerProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.Date;
import java.util.List;

import static com.intellij.java.codeInsight.navigation.TempRunConfigurationsKt.tempRunConfigurationsFixture;
import static com.intellij.junit.testFramework.MavenTestLib.JUNIT3;
import static com.intellij.junit.testFramework.MavenTestLib.JUNIT4;
import static com.intellij.junit.testFramework.MavenTestLib.JUNIT5;
import static com.intellij.junit.testFramework.MavenTestLib.PIONEER;
import static com.intellij.pom.java.LanguageLevel.HIGHEST;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JUnitTestRunLineMarkerTest {
  public static final LightProjectDescriptor descriptor = new JUnitProjectDescriptor(HIGHEST, JUNIT3, JUNIT4, JUNIT5, PIONEER);

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(descriptor);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private final TestFixture<TempRunConfigurations> runConfigurations = tempRunConfigurationsFixture(codeInsightFixture);

  @Test
  public void testAbstractTestClassMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java", """
      public abstract class MyTest extends junit.framework.TestCase {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> gutters = myFixture.findGuttersAtCaret();
    List<GutterMark> marks = ReadAction.computeBlocking(() -> gutters.stream().filter(m -> m.getTooltipText().equals("Run Test")).toList());
    assertEquals(1, marks.size());
  }

  @Test
  public void testNestedTestClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testUrl = "java:suite://Main$MainTest";
    myFixture.configureByText("MainTest.java", """
      public class Main {
        public static class Main<caret>Test extends junit.framework.TestCase {
          public void testFoo() {
          }
        }}""");
    doTestState(testUrl, TestStateInfo.Magnitude.FAILED_INDEX.getValue(), AllIcons.RunConfigurations.TestState.Red2);
  }

  @Test
  public void testTestAnnotationInSuperMethodOnly() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class Foo { @Test public void testFoo() {}}");
    myFixture.configureByText("MyTest.java", """
      public class MyTest extends Foo {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  @Test
  public void testAbstractMethodOverriddenWithTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      import org.junit.jupiter.api.Test;
      public class InheritedTest extends AbstractTestClass {
          @Override
          @Test
          void testFromBase() {}
      }
      """);
    myFixture.configureByText("AbstractTestClass.java", """
      public abstract class AbstractTestClass {
          abstract void test<caret>FromBase();
      }
      """);
    List<GutterMark> gutters = myFixture.findGuttersAtCaret();
    List<GutterMark> marks = ReadAction.computeBlocking(() -> gutters.stream().filter(m -> m.getTooltipText().equals("Run Test")).toList());
    assertEquals(1, marks.size());
  }

  @Test
  public void testTestClassWithMain() {
    doTestClassWithMain(null);
  }

  @Test
  public void testTestClassWithMainTestConfigurationExists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(myFixture.getProject());
      JUnitConfiguration test = new JUnitConfiguration("MainTest", myFixture.getProject());
      test.beClassConfiguration(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      runConfigurations.get().getTempSettings().add(settings);
    });
  }

  @Test
  public void testTestClassWithMainMainConfigurationExists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(myFixture.getProject());
      ApplicationConfiguration test = new ApplicationConfiguration("MainTest.main()", myFixture.getProject());
      test.setMainClass(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      runConfigurations.get().getTempSettings().add(settings);
    });
  }

  @Test
  public void testDisabledTestMethodWithGradleConfiguration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestWithDisabledAnnotation(new MockGradleRunConfiguration(myFixture.getProject(), "DisabledMethodTest"), 0, """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      
      class DisabledMethodTest {
        @Disabled
        @Test
        public void testDisabled<caret>() {}
      }
      """);
  }

  @Test
  public void testDisabledTestMethodWithJunitConfiguration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestWithDisabledAnnotation(new JUnitConfiguration("DisabledMethodTest", myFixture.getProject()), 1, """
       import org.junit.jupiter.api.Disabled;
       import org.junit.jupiter.api.Test;
      
       class DisabledMethodTest {
         @Disabled
         @Test
         public void testDisabled<caret>() {}
       }
      """);
  }

  @Test
  public void testDisabledTestClassWithGradleConfiguration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // For classes we always show the run line marker, even when no tests are runnable
    // This is because calculating whether to show the line marker here is complex and it not showing up at all might be confusing for users
    doTestWithDisabledAnnotation(new MockGradleRunConfiguration(myFixture.getProject(), "DisabledMethodTest"), 1, """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      
      class Disabled<caret>MethodTest {
        @Disabled
        @Test
        public void testDisabled() {}
      
        @Disabled
        @Test
        public void testAlsoDisabled() {}
      }
      """);
  }

  @Test
  public void testDisabledTestClassWithNonDisabledTestGradleConfiguration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestWithDisabledAnnotation(new MockGradleRunConfiguration(myFixture.getProject(), "DisabledMethodTest"), 1, """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      
      class Disabled<caret>MethodTest {
        @Disabled
        @Test
        public void testDisabled() {}
      
        @Test
        public void testNotDisabled() {}
      }
      """);
  }

  @Test
  public void testDisabledTestClassWithJunitConfiguration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTestWithDisabledAnnotation(new JUnitConfiguration("DisabledMethodTest", myFixture.getProject()), 1, """
       import org.junit.jupiter.api.Disabled;
       import org.junit.jupiter.api.Test;
      
       class Disabled<caret>MethodTest {
         @Disabled
         @Test
         public void testDisabled() {}
       }
      """);
  }

  @Test
  public void testClassInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("MyTest.java", """
      public class My<caret>Test extends junit.framework.TestCase {
          public void testFoo() {
          }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
      TestRunLineMarkerProvider provider = new TestRunLineMarkerProvider();
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
        RunLineMarkerContributor.Info info = provider.getInfo(psiClass.getNameIdentifier());
        assertNotNull(info);
      });
    });
  }

  @Test
  public void testRetryingTestAnnotationSingle() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("ClassWithRetryingTest.java", """
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTst {
          @RetryingTest(value = 2)
          void <caret>retryingTest() {
          }
      }
      """);
    GutterMark mark = findSingleGutterAtCaret();
    assertEquals(ExecutionBundle.message("run.text"), ReadAction.computeBlocking(() -> mark.getTooltipText()));
  }

  @Test
  public void testRetryingTestAnnotationWithRegularTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("ClassWithRetryingTest.java", """
      import org.junit.jupiter.api.Test;
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTest {
          @RetryingTest(value = 2)
          void <caret>retryingTest() {
          }
      
          @Test
          void regularTest() {
          }
      }
      """);

    GutterMark mark = findSingleGutterAtCaret();
    assertEquals(ExecutionBundle.message("run.text"), ReadAction.computeBlocking(() -> mark.getTooltipText()));
  }

  @Test
  public void testRetryingTestAnnotationWithDifferentParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("ClassWithRetryingTestWithDifferentParameters.java", """
      import org.junit.jupiter.api.Test;
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTestWithDifferentParameters {
          @RetryingTest(maxAttempts = 2, minSuccess = 1, suspendForMs = 0)
          void <caret>retryingTest() {
          }
      
          @Test
          void regularTest() {
          }
      }
      """);
    GutterMark mark = findSingleGutterAtCaret();
    assertEquals(ExecutionBundle.message("run.text"), ReadAction.computeBlocking(() -> mark.getTooltipText()));
  }

  @Test
  public void testRetryingTestAnnotationSupportGradle() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    myFixture.configureByText("ClassWithRetryingTest.java", """
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTest {
          @RetryingTest(value = 2)
          void <caret>retryingTest() {
          }
      }
      """);
    runConfigurations.get().setupRunConfiguration(new MockGradleRunConfiguration(myFixture.getProject(), "ClassWithRetryingTest"));
    doTestState("java:suite://ClassWithRetryingTest/retryingTest", TestStateInfo.Magnitude.PASSED_INDEX.getValue(),
                AllIcons.RunConfigurations.TestState.Green2);
  }

  private void doTestClassWithMain(Runnable setupExisting) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MainTest.java", """
      public class <caret>MainTest extends junit.framework.TestCase {
          public static void main(String[] args) {
          }
          public void testFoo() {
          }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      if (setupExisting != null) {
        setupExisting.run();
      }
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
      ActionGroup group = mark.getPopupMenuActions();
      assertNotNull(group);
      AnActionEvent event = TestActionEvent.createTestEvent();
      PresentationFactory factory = new PresentationFactory();
      List<AnAction> list = ContainerUtil.findAll(Utils.expandActionGroup(
        group, factory, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN, ActionUiKind.NONE), action -> {
        String text = factory.getPresentation(action).getText();
        return text != null && text.startsWith("Run '") && text.endsWith("'");
      });
      assertEquals(2, list.size(), list.toString());
      list.get(0).update(event);
      assertEquals("Run 'MainTest.main()'", event.getPresentation().getText());
      list.get(1).update(event);
      assertEquals("Run 'MainTest'", event.getPresentation().getText());
      myFixture.testAction(list.get(1));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(myFixture.getProject()).getSelectedConfiguration();
      runConfigurations.get().getTempSettings().add(selectedConfiguration);
      assertEquals("MainTest", selectedConfiguration.getName());
    });
  }

  private void doTestWithDisabledAnnotation(RunConfiguration configuration, int marksCount, String testClass) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    runConfigurations.get().setupRunConfiguration(configuration);

    myFixture.configureByText("DisabledMethodTest.java", testClass);
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(marksCount, marks.size());
  }

  private void doTestState(String testUrl, int indexValue, @NotNull Icon expectedIcon) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestStateStorage stateStorage = TestStateStorage.getInstance(myFixture.getProject());
    try {
      stateStorage.writeState(testUrl, new TestStateStorage.Record(indexValue, new Date(), 0, 0, "",
                                                                   "", ""));


      RunLineMarkerContributor.Info info = ReadAction.computeBlocking(
        () -> new TestRunLineMarkerProvider().getInfo(myFixture.getFile().findElementAt(myFixture.getCaretOffset())));
      assertNotNull(info);
      assertEquals(expectedIcon, info.icon);
    }
    finally {
      stateStorage.removeState(testUrl);
    }
  }

  private @NotNull GutterMark findSingleGutterAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<GutterMark> markList = myFixture.findGuttersAtCaret();
    assertThat(markList).hasSize(1);
    return ContainerUtil.getOnlyItem(markList);
  }
}
