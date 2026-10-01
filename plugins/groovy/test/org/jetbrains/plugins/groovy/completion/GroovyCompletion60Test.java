// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

/**
 * @author Bas Leijdekkers
 */
public final class GroovyCompletion60Test extends GroovyCompletionTestBase {

  public void testNoDuplicates() {
    myFixture.configureByText("A.groovy", """
      class Existence {
      
        static void main(String[] args) {
          java.util.List<String> l = of<caret>
        }
      }
      """);

    myFixture.complete(CompletionType.SMART, 2);
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotSame(elements[0].getPsiElement(), elements[1].getPsiElement());
  }


  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_6_0_REAL_JDK;
  }
}
