/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.actions.generate

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterAction
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterSetterAction
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateSetterAction
import org.jetbrains.plugins.groovy.actions.generate.constructors.GroovyGenerateConstructorAction
import org.jetbrains.plugins.groovy.actions.generate.equals.GroovyGenerateEqualsAction
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGenerateMethodMissingAction
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGeneratePropertyMissingAction
import org.jetbrains.plugins.groovy.actions.generate.tostring.GroovyGenerateToStringAction

class GroovyGenerateActionsTest extends LightPlatformCodeInsightTestCase {

  void 'test generate actions enabled for groovy files'() {
    configureFromFileText 'a.groovy', '''\
class Test {
  def field
  def anotherField

  <caret>
}
'''
    def actions = [
      new GroovyGenerateEqualsAction(),
      new GroovyGenerateGetterAction(),
      new GroovyGenerateSetterAction(),
      new GroovyGenerateGetterSetterAction(),
      new GroovyGenerateConstructorAction(),
      new GroovyGeneratePropertyMissingAction(),
      new GroovyGenerateMethodMissingAction(),
      new GroovyGenerateToStringAction()
    ]
    actions.each {
      def event = new TestActionEvent(getCurrentEditorDataContext(), it)
      it.update(event)
      assert event.presentation.enabledAndVisible
    }
  }

  void 'test generate actions disabled for non-groovy files'() {
    configureFromFileText 'a.java', '''\
class Test {
  String field;
  int anotherField;

  <caret>
}
'''
    def actions = [
      new GroovyGenerateEqualsAction(),
      new GroovyGenerateGetterAction(),
      new GroovyGenerateSetterAction(),
      new GroovyGenerateGetterSetterAction(),
      new GroovyGenerateConstructorAction(),
      new GroovyGeneratePropertyMissingAction(),
      new GroovyGenerateMethodMissingAction(),
      new GroovyGenerateToStringAction()
    ]
    actions.each {
      def event = new TestActionEvent(getCurrentEditorDataContext(), it)
      it.update(event)
      assert !event.presentation.enabledAndVisible
    }
  }
}
