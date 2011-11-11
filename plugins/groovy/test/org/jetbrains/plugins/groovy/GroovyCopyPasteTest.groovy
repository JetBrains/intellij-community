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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.openapi.actionSystem.IdeActions

/**
 * @author peter
 */
class GroovyCopyPasteTest extends LightCodeInsightFixtureTestCase {

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

  public void testEscapeDollarInGString() {
    myFixture.configureByText 'a.groovy', '''def x = '<selection>$a</selection>b/'''
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', 'def x = "smth<caret>h"'
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult 'def x = "smth$a<caret>h"'

  }

}
