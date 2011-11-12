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

  public void testGenerallyFocusLookup() {
    myFixture.configureByText("a.groovy", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    edt { myFixture.doHighlighting() }
    type 'x'
    assert lookup.focused
  }

  public void testTopLevelFocus() {
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'p'
    assert lookup.focused
  }

  public void testNoLookupFocusInVariable() {
    myFixture.configureByText("a.groovy", """StringBuffer st<caret>""")
    type 'r'
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.groovy", """xxx.<caret>""")
    type 'h' //hashCode
    assert !lookup.focused
  }

  public void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.groovy", """
      def foo(xxx) {
        xxx.<caret>
      }""")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter() {
    myFixture.configureByText("a.groovy", "{ <caret> }")
    type 'h'
    assert !lookup.focused
  }

  public void testPossibleClosureParameter2() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText("a.groovy", "{ a, <caret> }")
    type 'h'
    assert !lookup.focused
  }

  public void testImpossibleClosureParameter() {
    myFixture.configureByText("a.groovy", "String a; { a.<caret> }")
    type 'h'
    assert lookup.focused
  }

  public void testFieldTypeLowercase() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText "a.groovy", "class Foo { <caret> }"
    type 'aioobe'
    assert myFixture.lookupElementStrings == [ArrayIndexOutOfBoundsException.simpleName]
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

  public void testTypingNonImportedClassName() {
    setFocusLookup()

    myFixture.addClass("package foo; public class Foo239 {} ")
    myFixture.addClass("class Foo239Util {} ")
    myFixture.configureByText "a.groovy", "<caret>"
    type 'Foo239 '
    myFixture.checkResult 'Foo239 <caret>'
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.AUTOPOPUP_FOCUS_POLICY = CodeInsightSettings.SMART
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  private def setFocusLookup() {
    CodeInsightSettings.instance.AUTOPOPUP_FOCUS_POLICY = CodeInsightSettings.ALWAYS
  }

  public void testPopupAfterDotAfterPackage() {
    setFocusLookup()

    myFixture.configureByText 'a.groovy', '<caret>'
    type 'import jav'
    assert lookup
    type '.'
    assert lookup
  }

  public void testTypingFirstVarargDot() {
    myFixture.addClass("class Foo { static class Bar {} }")
    myFixture.configureByText "a.groovy", "void foo(Foo<caret>[] a) { }"
    type '.'
    assert !lookup
  }

  public void testTypingFirstVarargDot2() {
    myFixture.addClass("class Foo { static class Bar {} }")
    myFixture.configureByText "a.groovy", "void foo(Foo<caret>) { }"
    type '.'
    assert !lookup
  }

  public void testDotDot() {
    myFixture.configureByText "a.groovy", '2<caret>'
    type '.'
    assert lookup
    assert lookup.focused
    type '.'
    assert !lookup
    myFixture.checkResult '2..<caret>'
  }

  public void testInsideBuilderMethod() {
    myFixture.configureByText 'a.groovy', 'html { body {}; <caret> }'
    type 'h'
    assert lookup
    assert !lookup.focused
  }

  public void testInsideClosure() {
    myFixture.configureByText 'a.groovy', 'def cl = { foo(); <caret> }'
    type 'h'
    assert lookup
    assert lookup.focused
  }

  public void testForVariableNoFocus() {
    myFixture.configureByText 'a.groovy', 'def fl, cl = []; for(<caret>)'
    type 'f'
    assert !lookup.focused
    type 'inal '
    assert !lookup
    type 'c'
    assert !lookup.focused
    assert myFixture.editor.document.text.contains('for(final c)')
    type ' in c'
    assert lookup.focused
  }

  public void testNonImportedClass() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'Abcde '
    myFixture.checkResult 'import foo.Abcdefg\n\nAbcdefg <caret>'
  }

  public void testTwoNonImportedClasses() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'Abcde '
    myFixture.checkResult 'Abcdefg <caret>'
  }

  public void testPrivate() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.addClass("package foo; public class PrimaBalerina {}")
    myFixture.configureByText 'a.groovy', 'class Foo { <caret> }'
    type 'pri'
    assert myFixture.lookupElementStrings[0] == 'private'
    assert !('PrimaBalerina' in myFixture.lookupElementStrings)
  }

  public void testFieldTypeNonImported() {
    myFixture.addClass("package foo; public class PrimaBalerina {}")
    myFixture.configureByText 'a.groovy', 'class Foo { <caret> }'
    type 'PrimaB'
    assert myFixture.lookupElementStrings == ['PrimaBalerina']
  }

}
