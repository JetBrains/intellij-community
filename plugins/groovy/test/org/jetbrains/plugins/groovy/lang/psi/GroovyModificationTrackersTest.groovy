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
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GroovyModificationTrackersTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test class method'() {
    doTest '''\
class A {
  def foo() { <caret> } 
}
''', false
  }

  void 'test class initializer'() {
    doTest '''\
class A {
 {
   <caret>
 }
}
''', false
  }

  void 'test class body'() {
    doTest '''\
class A {
  <caret>
}
''', true
  }

  void 'test script body'() {
    doTest '<caret>', false
  }

  void 'test script variable'() {
    doTest 'def a<caret>= 1', true
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void doTest(String text, boolean shouldChange) {
    fixture.configureByText '_.groovy', text
    def (beforeStructure, beforeOutOfCodeBlock) = getModificationCounts()
    fixture.type " "
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    def (afterStructure, afterOutOfCodeBlock) = getModificationCounts()
    if (shouldChange) {
      assert beforeStructure < afterStructure
      assert beforeOutOfCodeBlock < afterOutOfCodeBlock
    }
    else {
      assert beforeStructure == afterStructure
      assert beforeOutOfCodeBlock == afterOutOfCodeBlock
    }
  }

  @CompileStatic
  List<Long> getModificationCounts() {
    psiManager.modificationTracker.with {
      [javaStructureModificationCount, outOfCodeBlockModificationCount]
    }
  }
}
