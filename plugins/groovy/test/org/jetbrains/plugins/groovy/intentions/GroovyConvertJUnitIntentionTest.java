/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class GroovyConvertJUnitIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testAssertFalse() {
    doTest("""
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assertFalse<caret>("!!!", 1 == 2)
               }
             }
             """, """
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assert !(1 == 2): "!!!"
               }
             }
             """);
  }

  @Test
  public void testAssertEquals() {
    doTest("""
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assertEquals<caret>("!!!", 1, 2)
               }
             }
             """, """
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assert 1 == 2: "!!!"
               }
             }
             """);
  }

  @Test
  public void testAssertNotSame() {
    doTest("""
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assertNotSame<caret>("1", "2")
               }
             }
             """, """
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assert !"1".is("2")
               }
             }
             """);
  }

  @Test
  public void testFail() {
    doTest("""
             class A extends junit.framework.Assert {
               public void testXxxx() {
                   assertSame<caret>("1")
               }
             }
             """, null);
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("junit/framework/Assert.groovy", """
      package junit.framework;
      public class Assert {
        public static void assertTrue(java.lang.String message, boolean condition) { }
        public static void assertTrue(boolean condition) { }
        public static void assertFalse(java.lang.String message, boolean condition) { }
        public static void assertEquals(java.lang.Object expected, java.lang.Object actual) { }
        public static void assertNotSame(java.lang.Object expected, java.lang.Object actual) { }
      }
      """);
    myFixture.configureByText("A.groovy", before);

    String hint = GroovyIntentionsBundle.message("convert.junit.assertion.to.assert.statement.intention.name");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (after == null) {
      assertThat(list).isNullOrEmpty();
      return;
    }

    myFixture.launchAction(assertThat(list).singleElement().actual());
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();

    myFixture.checkResult(after);
  }
}
