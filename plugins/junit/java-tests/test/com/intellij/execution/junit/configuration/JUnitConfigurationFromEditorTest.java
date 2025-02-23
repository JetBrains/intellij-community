// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.Set;

public class JUnitConfigurationFromEditorTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.addClass("package org.junit; public @interface Test{}");
    myFixture.addClass("package junit.framework; public class TestCase{}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith{ Class<?> value();}");
  }

  private <T> T setupConfigurationContext(final String fileText) {
    myFixture.configureByText("MyTest.java", fileText);

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myFixture.getProject());
    dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
    dataContext.put(CommonDataKeys.PSI_FILE, myFixture.getFile());

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    //noinspection unchecked
    return settings != null ? (T)settings.getConfiguration() : null;
  }

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
    assertSameElements(patterns, "MyTest,t1", "MyTest,t2");
  }

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
    assertSameElements(patterns, "MyTest,t1", "MyTest,t2");
  }

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
