// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.java.codeInsight.navigation.LineMarkerTestCase;
import com.intellij.java.codeInsight.navigation.MockGradleRunConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testIntegration.TestRunLineMarkerProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Date;
import java.util.List;

public class JUnitTestRunLineMarkerTest extends LineMarkerTestCase {
  public void testAbstractTestClassMethods() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MyTest.java", """
      public abstract class MyTest extends junit.framework.TestCase {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testNestedTestClass() {
    String testUrl = "java:suite://Main$MainTest";
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
        public class Main {
          public static class Main<caret>Test extends junit.framework.TestCase {
            public void testFoo() {
            }
          }}""");
    doTestState(testUrl, TestStateInfo.Magnitude.FAILED_INDEX.getValue(), AllIcons.RunConfigurations.TestState.Red2);
  }

  public void testTestAnnotationInSuperMethodOnly() {
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("class Foo { @Test public void testFoo() {}}");
    myFixture.configureByText("MyTest.java", """
      public class MyTest extends Foo {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testTestClassWithMain() {
    doTestClassWithMain(null);
  }

  public void testTestClassWithMainTestConfigurationExists() {
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(getProject());
      JUnitConfiguration test = new JUnitConfiguration("MainTest", getProject());
      test.beClassConfiguration(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      myTempSettings.add(settings);
    });
  }

  public void testTestClassWithMainMainConfigurationExists() {
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(getProject());
      ApplicationConfiguration test = new ApplicationConfiguration("MainTest.main()", getProject());
      test.setMainClass(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      myTempSettings.add(settings);
    });
  }

  public void testDisabledTestMethodWithGradleConfiguration() {
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

  public void testDisabledTestMethodWithJunitConfiguration() {
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

  public void testDisabledTestClassWithGradleConfiguration() {
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

  public void testDisabledTestClassWithNonDisabledTestGradleConfiguration() {
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

  public void testDisabledTestClassWithJunitConfiguration() {
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

  public void testClassInDumbMode() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    PsiFile file = myFixture.configureByText("MyTest.java", """
      public class My<caret>Test extends junit.framework.TestCase {
          public void testFoo() {
          }
      }""");
    PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
    TestRunLineMarkerProvider provider = new TestRunLineMarkerProvider();
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      RunLineMarkerContributor.Info info = provider.getInfo(psiClass.getNameIdentifier());
      assertNotNull(info);
    });
  }

  public void testRetryingTestAnnotationSingle() {
    JUnit5TestFrameworkSetupUtil.setupJunit5WithExtensionLibrary(myFixture);
    myFixture.configureByText("ClassWithRetryingTest.java", """
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTst {
          @RetryingTest(value = 2)
          void <caret>retryingTest() {
          }
      }
      """);
    GutterMark mark = findSingleGutterAtCaret();
    assertEquals(ExecutionBundle.message("run.text"), mark.getTooltipText());
  }

  public void testRetryingTestAnnotationWithRegularTest() {
    JUnit5TestFrameworkSetupUtil.setupJunit5WithExtensionLibrary(myFixture);
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
    assertEquals(ExecutionBundle.message("run.text"), mark.getTooltipText());
  }

  public void testRetryingTestAnnotationWithDifferentParameters() {
    JUnit5TestFrameworkSetupUtil.setupJunit5WithExtensionLibrary(myFixture);
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
    assertEquals(ExecutionBundle.message("run.text"), mark.getTooltipText());
  }

  public void testRetryingTestAnnotationSupportGradle() {
    JUnit5TestFrameworkSetupUtil.setupJunit5WithExtensionLibrary(myFixture);

    myFixture.configureByText("ClassWithRetryingTest.java", """
      import org.junitpioneer.jupiter.RetryingTest;
      
      public class ClassWithRetryingTest {
          @RetryingTest(value = 2)
          void <caret>retryingTest() {
          }
      }
      """);
    setupRunConfiguration(new MockGradleRunConfiguration(myFixture.getProject(), "ClassWithRetryingTest"));
    doTestState("java:suite://ClassWithRetryingTest/retryingTest", TestStateInfo.Magnitude.PASSED_INDEX.getValue(), AllIcons.RunConfigurations.TestState.Green2);

  }

  private void doTestClassWithMain(Runnable setupExisting) {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class <caret>MainTest extends junit.framework.TestCase {
          public static void main(String[] args) {
          }
          public void testFoo() {
          }
      }""");
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
    assertEquals(list.toString(), 2, list.size());
    list.get(0).update(event);
    assertEquals("Run 'MainTest.main()'", event.getPresentation().getText());
    list.get(1).update(event);
    assertEquals("Run 'MainTest'", event.getPresentation().getText());
    myFixture.testAction(list.get(1));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(getProject()).getSelectedConfiguration();
    myTempSettings.add(selectedConfiguration);
    assertEquals("MainTest", selectedConfiguration.getName());
  }

  private void doTestWithDisabledAnnotation(RunConfiguration configuration, int marksCount, String testClass) {
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.addClass("package org.junit.jupiter.api; public @interface Disabled {}");

    setupRunConfiguration(configuration);

    myFixture.configureByText("DisabledMethodTest.java", testClass);
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(marksCount, marks.size());
  }

  private void doTestState(String testUrl, int indexValue, @NotNull Icon expectedIcon) {
    TestStateStorage stateStorage = TestStateStorage.getInstance(getProject());
    try {
      stateStorage.writeState(testUrl, new TestStateStorage.Record(indexValue, new Date(), 0, 0, "",
                                                                   "", ""));


      RunLineMarkerContributor.Info info = new TestRunLineMarkerProvider().getInfo(myFixture.getFile().findElementAt(myFixture.getCaretOffset()));
      assertNotNull(info);
      assertEquals(expectedIcon, info.icon);
    }
    finally {
      stateStorage.removeState(testUrl);
    }
  }

  private @NotNull GutterMark findSingleGutterAtCaret() {
    List<GutterMark> markList = myFixture.findGuttersAtCaret();
    assertSize(1, markList);
    return ContainerUtil.getOnlyItem(markList);
  }
}
