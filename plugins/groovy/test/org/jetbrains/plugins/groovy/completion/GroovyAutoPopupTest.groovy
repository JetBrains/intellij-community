/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.LightGroovyTestCase

/**
 * @author peter
 */
class GroovyAutoPopupTest extends CompletionAutoPopupTestCase {
  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return LightGroovyTestCase.GROOVY_DESCRIPTOR
  }

  public void testGenerallyNoFocusLookup() {
    myFixture.configureByText("a.groovy", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    edt { myFixture.doHighlighting() }
    type 'x'
    assert !lookup.focused
  }

  public void testGenerallyFocusLookupInGpp() {
    myFixture.configureByText("a.gpp", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    type 'x'
    assert lookup.focused
  }

  public void testNoLookupFocusInVariable() {
    myFixture.configureByText("a.gpp", """StringBuffer st<caret>""")
    type 'r'
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.gpp", """xxx.<caret>""")
    type 'h' //hashCode
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.gpp", """
      def foo(xxx) {
        xxx.<caret>
      }""")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter() {
    myFixture.configureByText("a.gpp", "{ <caret> }")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter2() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    try {
      myFixture.configureByText("a.gpp", "{ a, <caret> }")
      type 'h'
      assert !lookup.focused
    }
    finally {
      CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    }
  }

  public void testImpossibleClosureParameter() {
    myFixture.configureByText("a.gpp", "String a; { a.<caret> }")
    type 'h'
    assert lookup.focused
  }

  public void testFieldTypeLowercase() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    try {
      myFixture.configureByText "a.groovy", "class Foo { <caret> }"
      type 'aioobe'
      assert myFixture.lookupElementStrings == [ArrayIndexOutOfBoundsException.simpleName]
    }
    finally {
      CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    }
  }

  public void testNoWordCompletionAutoPopup() {
    myFixture.configureByText "a.groovy", 'def foo = "f<caret>"'
    type 'o'
    assert !lookup
  }

  public void testNoClassesInUnqualifiedImports() {
    myFixture.addClass("package xxxxx; public class Xxxxxxxxx {}")
    myFixture.configureByText 'a.groovy', 'package foo; import <caret>'
    type 'xxx'
    assert myFixture.lookupElementStrings == ['xxxxx']
  }


}
