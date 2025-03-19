/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class InjectionNotApplicableTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    var inspection = new InjectionNotApplicable();
    myFixture.enableInspections(inspection);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testNonString() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      class Hello {
                          <error descr="Language injection is only applicable to strings or string templates">@Language("JAVA")</error>
                          public static native Object getSomething();
                      }""");
  }

  public void testNonStringFix() {
    quickFixTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      class Hello {
                          @<caret>Language("JAVA")
                          public static native Object getSomething();
                      }""",
                 """
                      class Hello {
                          public static native Object getSomething();
                      }""", 
                 "Remove annotation");
  }
  
  public void testProcessorFromMethod() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      interface MyProcessor extends StringTemplate.Processor<Object, RuntimeException> {}
                      
                      class Hello {
                          @Language("JAVA")
                          public static native MyProcessor getProcessor();
                      }""");

  }

  public void highlightTest(String text) {
    myFixture.configureByText("X.java", text);
    myFixture.testHighlighting();
  }

  public void quickFixTest(String before, String after, String hint) {
    myFixture.configureByText("X.java", before);
    IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResult(after);
  }
}