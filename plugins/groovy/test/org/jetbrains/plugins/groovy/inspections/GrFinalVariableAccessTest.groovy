/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.control.GrFinalVariableAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

/**
 * @author Max Medvedev
 */
class GrFinalVariableAccessTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() {
    return [new GrFinalVariableAccessInspection()]
  }


  void testSimpleVar() {
    testHighlighting('''
      final foo = 5
      <warning>foo</warning> = 7
      print foo
    ''')
  }

  void testSplitInit() {
    testHighlighting('''
      final foo
      foo = 7
      <warning>foo</warning> = 8
      print foo
    ''')
  }

  void testIf1() {
    testHighlighting('''
      final foo = 5
      if (cond) {
        <warning>foo</warning> = 7
      }
      print foo
    ''')
  }

  void testIf2() {
    testHighlighting('''
      final foo
      if (cond) {
        foo = 7
      }
      else {
        foo = 2
      }
      <warning>foo</warning> = 1
      print foo
    ''')
  }

  void testIf3() {
    testHighlighting('''
      final foo
      if (cond) {
        foo = 7
      }
      <warning>foo</warning> = 1
      print foo
    ''')
  }


  void testFor() {
    testHighlighting('''
      for (a in b) {
        final x = 5  //all correct
        print x
      }
    ''')
  }

  void testFor2() {
    testHighlighting('''
      final foo = 5
      for (a in b) {
        <warning>foo</warning> = 5
        print foo
      }
    ''')
  }

  void testFor3() {
    testHighlighting('''
      for (a in b)
        final foo = 5  //correct code
    ''')
  }


  void testForParam() {
    testHighlighting('''
      for (final i : [1, 2]) {
        <warning>i</warning> = 5
        print i
      }
    ''')
  }

  void testDuplicatedVar() {
    testHighlighting('''
      if (cond) {
        final foo = 5
        print foo
      }

      if (otherCond) {
        final foo = 2  //correct
        <warning>foo</warning> = 4
        print foo
      }

      if (anotherCond)
        final foo = 3 //correct
''')
  }

  void testDuplicatedVar2() {
    testHighlighting('''
      if (cond) {
        final foo = 5
        <warning>foo</warning> = 4
        print foo
      }

      if (otherCond) {
        foo = 4
        print foo
      }

      if (anotherCond)
        final foo = 3 //correct
''')
  }

  void testDuplicatedVar3() {
    testHighlighting('''
      class X {
        def bar() {
          if (cond) {
            final foo = 5
            <warning>foo</warning> = 4
            print foo
          }

          if (otherCond) {
            foo = 4
            print foo
          }

          if (anotherCond)
            final foo = 3 //correct
        }
      }
''')
  }
}
