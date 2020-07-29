// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Ignore
import org.junit.Test

@Ignore
@CompileStatic
class GroovyModificationTrackersTest extends GroovyLatestTest {

  @Test
  void 'test class method'() {
    doTest '''\
class A {
  def foo() { <caret> } 
}
''', false
  }

  @Test
  void 'test class initializer'() {
    doTest '''\
class A {
 {
   <caret>
 }
}
''', false
  }

  @Test
  void 'test class body'() {
    doTest '''\
class A {
  <caret>
}
''', false, true
  }

  @Test
  void 'test script body'() {
    doTest '<caret>', false
  }

  @Test
  void 'test script variable'() {
    doTest 'def a<caret>= 1', false, true
  }

  void doTest(String text, boolean structureShouldChange, boolean oocbShouldChange = structureShouldChange) {
    fixture.configureByText '_.groovy', text
    def (long beforeStructure, long beforeOutOfCodeBlock) = [javaStructureCount, outOfCodeBlockCount]
    List<Throwable> changeTraces = []
    if (!structureShouldChange && !oocbShouldChange) {
      PsiModificationTracker.Listener listener = {
        def message = "java structure: $javaStructureCount, out of code block: $outOfCodeBlockCount"
        changeTraces << new Throwable(message)
      }
      fixture.project.messageBus.connect fixture.testRootDisposable subscribe PsiModificationTracker.TOPIC, listener
    }
    fixture.type " "
    PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
    def (long afterStructure, long afterOutOfCodeBlock) = [javaStructureCount, outOfCodeBlockCount]
    try {
      if (structureShouldChange) {
        assert beforeStructure < afterStructure
      }
      else {
        assert beforeStructure == afterStructure
      }
      if (oocbShouldChange) {
        assert beforeOutOfCodeBlock < afterOutOfCodeBlock
      }
      else {
        assert beforeOutOfCodeBlock == afterOutOfCodeBlock
      }
    }
    catch (Throwable e) {
      changeTraces*.printStackTrace()
      throw e
    }
  }

  private long getJavaStructureCount() { fixture.psiManager.modificationTracker.modificationCount }

  private long getOutOfCodeBlockCount() { fixture.psiManager.modificationTracker.modificationCount }
}
