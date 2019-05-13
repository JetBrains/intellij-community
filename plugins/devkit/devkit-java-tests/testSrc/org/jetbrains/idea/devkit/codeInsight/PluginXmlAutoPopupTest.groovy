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
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase

/**
 * @author peter
 */
class PluginXmlAutoPopupTest extends CompletionAutoPopupTestCase {

  void "test autopopup for class references"() {
    myFixture.addClass("public class FooFooFooFooFoo { }")
    myFixture.configureByText 'plugin.xml', '''
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="FooFooFooFooFox" interface="FFFF<caret>"/>
  </extensionPoints>
</idea-plugin>
'''
    type 'o'
    assert lookup
    assert myFixture.lookupElementStrings == ['FooFooFooFooFoo']
  }

  void "test no autopopup when only word completion is available"() {
    myFixture.configureByText 'plugin.xml', '''
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="<caret>"/>
  </extensionPoints>
</idea-plugin>
'''
    type 'e'
    assert !lookup
  }

}
