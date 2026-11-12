// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.bugs.NewInstanceOfSingletonInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SingletonNewInstanceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(NewInstanceOfSingletonInspection.class);
  }

  private void doTest(final String before, final String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", before);
    IntentionAction action = myFixture.findSingleIntention(GroovyBundle.message("replace.new.expression.with.instance.access"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResult(after);
  }

  @Test
  public void testFixSimple() {
    doTest("""
           @Singleton
           class A {}
           
           new <caret>A()
           """, """
           @Singleton
           class A {}
           
           A.instance
           """);
  }

  @Test
  public void testFixCustomPropertyName() {
    doTest("""
           @Singleton(property = "coolInstance")
           class A {}
           
           new <caret>A()
           """, """
           @Singleton(property = "coolInstance")
           class A {}
           
           A.coolInstance
           """);
  }
}
