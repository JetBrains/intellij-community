// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.junit.testFramework.MavenTestLib.JUNIT4;
import static com.intellij.junit.testFramework.MavenTestLib.JUNIT5;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JUnitConfigurationFromEditorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(new JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT4, JUNIT5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private <T> T setupConfigurationContext(final String fileText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MyTest.java", fileText);

    return ReadAction.computeBlocking(() -> {
      MapDataContext dataContext = new MapDataContext();
      dataContext.put(CommonDataKeys.PROJECT, myFixture.getProject());
      dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
      dataContext.put(CommonDataKeys.PSI_FILE, myFixture.getFile());

      ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
      RunnerAndConfigurationSettings settings = context.getConfiguration();
      //noinspection unchecked
      return settings != null ? (T)settings.getConfiguration() : null;
    });
  }

  @Test
  public void testPatternConfigurationFromSelection() {
    JUnitConfiguration configuration = setupConfigurationContext("""
                                                                   import org.junit.Test; public class MyTest {
                                                                   <selection>@Test
                                                                   public void t1(){}
                                                                   @Test
                                                                   public void t2(){}
                                                                   </selection>@Test
                                                                   public void t3(){}
                                                                   }""");
    Set<String> patterns = configuration.getPersistentData().getPatterns();
    assertThat(patterns).containsExactlyInAnyOrder("MyTest,t1", "MyTest,t2");
  }

  @Test
  public void testPatternConfigurationFromMultipleCarets() {
    JUnitConfiguration configuration = setupConfigurationContext("""
                                                                   import org.junit.Test; public class MyTest {
                                                                   @Test
                                                                   public void t<caret>1(){}
                                                                   @Test
                                                                   public void t<caret>2(){}
                                                                   @Test
                                                                   public void t3(){}
                                                                   }""");
    Set<String> patterns = configuration.getPersistentData().getPatterns();
    assertThat(patterns).containsExactlyInAnyOrder("MyTest,t1", "MyTest,t2");
  }

  @Test
  public void testConfigurationFromParameterizedValue() {
    JUnitConfiguration configuration = setupConfigurationContext(
      """
        import org.junit.jupiter.params.ParameterizedTest;
        import org.junit.jupiter.params.provider.ValueSource;

        public class MyTest {
          @ParameterizedTest
          @ValueSource(strings = {
            "racecar",
            "ra<caret>dar",
            "able was I ere I saw elba"
          })
        public void palindromes(String candidate) {}}""");
    String parameters = configuration.getPersistentData().getProgramParameters();
    assertEquals("valueSource 1", parameters);
  }

  @Test
  public void testStaticNestedClassWithAnnotations() {
    JUnitConfiguration configuration = setupConfigurationContext("""
                                                                   import org.junit.runner.RunWith; @RunWith(Suite.class)
                                                                   public class MyTest {
                                                                     @RunWith(Suite.class)
                                                                     public static class Nes<caret>ted {}
                                                                   }""");
    assertEquals("MyTest$Nested", configuration.getPersistentData().getMainClassName());
  }
}
