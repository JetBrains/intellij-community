// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SingletonConstructorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(SingletonConstructorInspection.class);
  }

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
            <error descr="@Singleton class should not have constructors">A</error>() {}
            <error descr="@Singleton class should not have constructors">A</error>(a) {}
        }
        
        @Singleton(strict=true)
        class ExplicitStrict {
          <error descr="@Singleton class should not have constructors">ExplicitStrict</error>() {}
          <error descr="@Singleton class should not have constructors">ExplicitStrict</error>(a) {}
        }
        
        @Singleton(strict = false)
        class NonStrict {
          NonStrict() {}
          NonStrict(a) {}
        }
        """);

    myFixture.checkHighlighting();
  }

  @Test
  public void testMakeNonStrictFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton(strict = false)
                    class A {
                      <caret>A() {}
                    }
                    """);
  }

  @Test
  public void testMakeNonStrictFixExisting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
        @Singleton(strict = true, property = "lol")
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton(strict = false, property = "lol")
                    class A {
                      <caret>A() {}
                    }
                    """);
  }

  @Test
  public void testRemoveConstructorFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.remove"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton
                    class A {
                    }
                    """);
  }
}
