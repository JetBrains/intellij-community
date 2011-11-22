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

  public void testEscapeSlashesInRegex() {
    myFixture.configureByText 'a.groovy', '<selection>a/b</selection>'
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = /smth<caret>/'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = /smtha\\/b<caret>/'
  }

  public void testEscapeSlashesInRegexFromRegex() {
    myFixture.configureByText 'a.groovy', 'def x = /<selection>a\\/b</selection>/'
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = /smth<caret>/'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = /smtha\\/b<caret>/'
  }
  
  void testDontEscapeSymbolsInRegex(){
    myFixture.configureByText 'a.groovy', '''def x = <selection>a/b</selection>'''
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = /<caret> /'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult '''def x = /a\\/b /'''
  }

  public void testEscapeDollarInGString() {
    myFixture.configureByText 'a.groovy', '''def x = '<selection>$a</selection>b/'''
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = "smth<caret>h"'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = "smth\\$a<caret>h"'

  }

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

  public void testRestoreImports() {
    myFixture.addClass("package foo; public class Foo {}")

    myFixture.configureByText 'a.groovy', '''import foo.*; <selection>Foo f</selection>'''
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', '<caret>'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult '''import foo.Foo

Foo f'''
  }

  public void testPasteMultilineIntoMultilineGString() throws Exception {
    myFixture.configureByText 'a.txt', '<selection>a/b\nc/d</selection>'
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = """smth<caret>"""'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = """smtha/b\nc/d<caret>"""'
  }

  public void testPasteMultilineIntoString() throws Exception {
    myFixture.configureByText 'a.txt', '<selection>a\nd</selection>'
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', "def x = 'smth<caret>'"
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult "def x = 'smtha\\n' +\n        'd<caret>'"
  }

  public void testPasteMultilineIntoGString() throws Exception {
    myFixture.configureByText 'a.txt', '<selection>a\nd</selection>'
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = "smth<caret>"'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = "smtha\\n" +\n        "d<caret>"'
  }

}
