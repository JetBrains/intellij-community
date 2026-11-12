// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class AddConstructorMatchingSuperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, HINT, TestUtils.getAbsoluteTestDataPath() + "intentions/constructorMatchingSuper/");

  private static final String HINT = "Create constructor matching super";

  @Test
  public void testGroovyToGroovy() {
    grIntention.get().doTextTest(
      """
        @interface Anno {}
        class Base {
            Base(int p, @Anno int x) throws Exception {}
        }
        
        class Derived exten<caret>ds Base {
        }
        """,
      """
        @interface Anno {}
        class Base {
            Base(int p, @Anno int x) throws Exception {}
        }
        
        class Derived extends Base {
            <caret>Derived(int p, int x) throws Exception {
                super(p, x)
            }
        }
        """);
  }

  @Test
  public void testJavaToGroovy() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addClass("""
                         @interface Anno {}
                         class Base {
                             Base(int p, @Anno int x) throws Exception {}
                         }
                         """);
    grIntention.get().doTextTest("""
                 class Derived exten<caret>ds Base {
                 }
                 """,
               """
                 class Derived extends Base {
                     <caret>Derived(int p, int x) throws Exception {
                         super(p, x)
                     }
                 }
                 """);
  }

  @Test
  public void testGroovyToJava() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addClass("""
                         class Base {
                             Base(int p, @Override int x) throws Exception {}
                         }
                         """);
    myFixture.configureByText("a.java", """
      class Derived exten<caret>ds Base {
      }
      """);
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(HINT);
    myFixture.launchAction(assertThat(list).singleElement().actual());
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    myFixture.checkResult(
      """
        class Derived extends Base {
            <caret>Derived(int p, int x) throws Exception {
                super(p, x);
            }
        }
        """);
  }
}
