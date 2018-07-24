// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

/**
 * @author peter
 */
class GroovyAutoPopupTest extends CompletionAutoPopupTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_1

  @Override
  protected void setUp() {
    super.setUp()
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = true
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  void testGenerallyFocusLookup() {
    myFixture.configureByText("a.groovy", """
        String foo(String xxxxxx) {
          return xx<caret>
        }
    """)
    runInEdtAndWait { myFixture.doHighlighting() }
    type 'x'
    assert lookup.focused
  }

  void testTopLevelFocus() {
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'p'
    assert lookup.focused
  }

  void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.groovy", """xxx.<caret>""")
    type 'h' //hashCode
    assert !lookup
  }

  void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.groovy", """
      def foo(xxx) {
        xxx.<caret>
      }""")
    type 'h'
    assert !lookup
  }

  void testImpossibleClosureParameter() {
    myFixture.configureByText("a.groovy", "String a; { a.<caret> }")
    type 'h'
    assert lookup.focused
  }

  void testFieldTypeLowercase() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText "a.groovy", "class Foo { <caret> }"
    type 'aioobe'
    assert myFixture.lookupElementStrings == [ArrayIndexOutOfBoundsException.simpleName]
  }

  void testNoWordCompletionAutoPopup() {
    myFixture.configureByText "a.groovy", 'def foo = "f<caret>"'
    type 'o'
    assert !lookup
  }

  void testClassesAndPackagesInUnqualifiedImports() {
    myFixture.addClass("package Xxxxx; public class Xxxxxxxxx {}")
    myFixture.configureByText 'a.groovy', 'package foo; import <caret>'
    type 'Xxx'
    assert myFixture.lookupElementStrings == ['Xxxxxxxxx', 'Xxxxx']
  }


  void testPopupAfterDotAfterPackage() {
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'import jav'
    assert lookup
    type '.'
    assert lookup
  }

  void testTypingFirstVarargDot() {
    myFixture.addClass("class Foo { static class Bar {} }")
    myFixture.configureByText "a.groovy", "void foo(Foo<caret>[] a) { }"
    type '.'
    assert lookup
    type '.'
    myFixture.checkResult('void foo(Foo..<caret>[] a) { }')
  }

  void testTypingFirstVarargDot2() {
    myFixture.addClass("class Foo { static class Bar {} }")
    myFixture.configureByText "a.groovy", "void foo(Foo<caret>) { }"
    type '.'
    assert lookup
    type '.'
    myFixture.checkResult('void foo(Foo..<caret>) { }')
  }

  void testDotDot() {
    myFixture.configureByText "a.groovy", '2<caret>'
    type '.'
    assert lookup
    assert lookup.focused
    type '.'
    assert !lookup
    myFixture.checkResult '2..<caret>'
  }

  void testInsideClosure() {
    myFixture.configureByText 'a.groovy', 'def cl = { foo(); <caret> }'
    type 'h'
    assert lookup
    assert lookup.focused
  }

  void testNonImportedClass() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'Abcde '
    myFixture.checkResult 'import foo.Abcdefg\n\nAbcdefg <caret>'
  }

  void "test two non-imported classes when space does not select first autopopup item"() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false

    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.groovy', 'class Foo extends <caret>'
    type 'Abcde'
    assert lookup.items.size() == 2
    runInEdtAndWait { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) }
    type ' '
    myFixture.checkResult '''import foo.Abcdefg

class Foo extends Abcdefg <caret>'''
  }


  void testTwoNonImportedClasses() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'Abcde '
    myFixture.checkResult '''import bar.Abcdefg

Abcdefg <caret>'''
  }

  void testPrivate() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText 'a.groovy', 'class Foo { <caret> }'
    type 'pri'
    assert myFixture.lookupElementStrings[0] == 'private'
  }

  void testFieldTypeNonImported() {
    myFixture.addClass("package foo; public class PrimaBalerina {}")
    myFixture.configureByText 'a.groovy', 'class Foo { <caret> }'
    type 'PrimaB'
    assert myFixture.lookupElementStrings == ['PrimaBalerina']
  }

  void testEnteringLabel() {
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'FIS:'
    assert myFixture.editor.document.text == 'FIS:'
  }

  void testEnteringNamedArg() {
    myFixture.configureByText 'a.groovy', 'foo(<caret>)'
    type 'has:'
    myFixture.checkResult 'foo(has:<caret>)'
  }

  void testEnteringMapKey() {
    myFixture.configureByText 'a.groovy', '[<caret>]'
    type 'has:'
    myFixture.checkResult '[has:<caret>]'
  }

  void testPreferRightCasedVariant() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText 'a.groovy', '<caret>'
    type 'boo'
    myFixture.assertPreferredCompletionItems 0, 'boolean'
    type '\b\b\bBoo'
    myFixture.assertPreferredCompletionItems 0, 'Boolean'
  }

  void testPackageQualifier() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false

    myFixture.addClass("package com.too; public class Util {}")
    myFixture.configureByText 'a.groovy', 'void foo(Object command) { <caret> }'
    type 'com.t'
    assert myFixture.lookupElementStrings.containsAll(['too', 'command.toString'])
  }

  void testVarargParenthesis() {
    myFixture.configureByText 'a.groovy', '''
void foo(File... files) { }
foo(new <caret>)
'''
    type 'File'
    myFixture.assertPreferredCompletionItems 0, 'File', 'File', 'FileInputStream'
    type '('
    assert myFixture.editor.document.text.contains('new File()')
  }

  void testNoAutopopupAfterDef() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText 'a.groovy', 'def <caret>'
    type 'a'
    assert !lookup
  }

  void "test expand class list when typing more or moving caret"() {
    myFixture.addClass 'package foo; public class KimeFamilyRange {}'
    myFixture.addClass 'package foo; public class FamiliesRangesMetaData {}'
    myFixture.addClass 'public class KSomethingInCurrentPackage {}'
    myFixture.configureByText 'a.groovy', '<caret>'

    type 'F'
    assert !myFixture.lookupElementStrings.contains('KimeFamilyRange')

    type 'aRa'
    myFixture.assertPreferredCompletionItems 0, 'FamiliesRangesMetaData', 'KimeFamilyRange'

    4.times {
      edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
      myTester.joinCompletion()
    }
    assert !myFixture.lookupElementStrings.contains('KimeFamilyRange')

    type 'K'

    4.times {
      edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
      myTester.joinCompletion()
    }

    myFixture.assertPreferredCompletionItems 0, 'KimeFamilyRange'
  }


}
