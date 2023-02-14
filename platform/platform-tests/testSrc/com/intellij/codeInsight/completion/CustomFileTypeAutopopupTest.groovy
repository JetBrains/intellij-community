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
package com.intellij.codeInsight.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import groovy.transform.CompileStatic

@CompileStatic
class CustomFileTypeAutopopupTest extends CompletionAutoPopupTestCase {
  void "test no autopopup when typing just digit in a custom file type"() {
    myFixture.configureByText 'a.hs', 'a42 = 42\n<caret> }}'
    type '4'
    assert !lookup
  }

  void "test show autopopup when typing digit after letter"() {
    myFixture.configureByText 'a.hs', 'a42 = 42\na<caret> }}'
    type '4'
    assert lookup
  }

}
