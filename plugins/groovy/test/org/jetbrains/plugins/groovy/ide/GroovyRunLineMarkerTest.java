// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ide;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyRunLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("ide.jvm.run.marker").setValue(true, myFixture.getTestRootDisposable());
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      class MainTest {
        static void <caret>foo(String[] args) {}
        static void main(String[] args) {}
      }
      """);
    assertTrue(myFixture.findGuttersAtCaret().isEmpty());
    assertEquals(2, myFixture.findAllGutters().size());
  }

  @Test
  public void testImplicitStringArray() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      class MainTest {
        static void main(args) {}
      }
      """);
    assertThat(myFixture.findAllGutters()).hasSize(2);
  }

  @Test
  public void testJavaClassInheritor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                              class Main {
                              public static void main(String[] args){}
                            }
                            """);
    myFixture.configureByText("_.groovy", """
      class MainTest extends Main {}
      """);
    assertEquals(1, myFixture.findAllGutters().size());
  }

  @Test
  public void testDefaultParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      class MainTest {
        static void main(String[] args, b = 2) {}
      }
      """);
    assertEquals(2, myFixture.findAllGutters().size());
  }
}
