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
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
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

  void doTest(String text, boolean shouldChange) {
    fixture.configureByText '_.groovy', text
    def (long beforeStructure, long beforeOutOfCodeBlock) = [javaStructureCount, outOfCodeBlockCount]
    List<Throwable> changeTraces = []
    if (!shouldChange) {
      PsiModificationTracker.Listener listener = {
        def message = "java scrtucture: $javaStructureCount, out of code block: $outOfCodeBlockCount"
        changeTraces << new Throwable(message)
      }
      project.messageBus.connect testRootDisposable subscribe PsiModificationTracker.TOPIC, listener
    }
    fixture.type " "
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    def (long afterStructure, long afterOutOfCodeBlock) = [javaStructureCount, outOfCodeBlockCount]
    try {
      if (shouldChange) {
        assert beforeStructure < afterStructure && beforeOutOfCodeBlock < afterOutOfCodeBlock
      }
      else {
        assert beforeStructure == afterStructure && beforeOutOfCodeBlock == afterOutOfCodeBlock
      }
    }
    catch (Throwable e) {
      changeTraces*.printStackTrace()
      throw e
    }
  }

  private long getJavaStructureCount() { psiManager.modificationTracker.javaStructureModificationCount }

  private long getOutOfCodeBlockCount() { psiManager.modificationTracker.outOfCodeBlockModificationCount }
}
