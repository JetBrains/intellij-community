/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class GroovyCopyPasteTest extends LightCodeInsightFixtureTestCase {
  int myAddImportsOld

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    settings.ADD_IMPORTS_ON_PASTE = myAddImportsOld;
    super.tearDown();
  }
  
  private void doTest(String fromFileName, String fromText, String toText, String expected) {
    myFixture.configureByText fromFileName, fromText
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', toText
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult expected
  }

  public void testEscapeSlashesInRegex() {
    doTest 'a.groovy', '<selection>a/b</selection>', 'def x = /smth<caret>/', 'def x = /smtha\\/b<caret>/'
  }

  public void testEscapeSlashesInRegexFromRegex() {
    doTest 'a.groovy', 'def x = / <selection>a\\/b</selection>/', 'def x = /smth<caret>/', 'def x = /smtha\\/b<caret>/'
  }

  void testDontEscapeSymbolsInRegex(){
    doTest 'a.groovy', '''def x = <selection>a/b</selection>''', 'def x = /<caret> /', '''def x = /a\\/b /'''
  }

  public void testEscapeDollarInGString() {
    doTest 'a.groovy', '''def x = '<selection>$a</selection>b/''', 'def x = "smth<caret>h"', 'def x = "smth\\$a<caret>h"'

  }

  public void testRestoreImports() {
    myFixture.addClass("package foo; public class Foo {}")

    doTest 'a.groovy', '''import foo.*; <selection>Foo f</selection>''', '<caret>', '''import foo.Foo

Foo f'''
  }

  public void testPasteMultilineIntoMultilineGString() throws Exception {
    doTest 'a.txt', '<selection>a/b\nc/d</selection>', 'def x = """smth<caret>"""', 'def x = """smtha/b\nc/d<caret>"""'
  }

  public void testPasteMultilineIntoString() throws Exception {
    doTest 'a.txt', '<selection>a\nd</selection>', "def x = 'smth<caret>'", "def x = 'smtha\\n' +\n        'd<caret>'"
  }

  public void testPasteMultilineIntoGString() throws Exception {
    doTest  'a.txt', '<selection>a\nd</selection>', 'def x = "smth<caret>"', 'def x = "smtha\\n" +\n        "d<caret>"'
  }

  public void testGStringEolReplace() throws Exception {
    doTest  'a.txt',
            '''<selection>first
second
</selection>''',
            '''def x = """
<selection>foo
</selection>"""''',
            '''def x = """
first
second
<caret>"""'''
  }

  void testPasteInGStringContent() {
    doTest 'a.groovy', 'def a = <selection>5\\6</selection>', 'def x = "<caret> "', 'def x = "5\\\\6 "'
  }

}
