// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.LocalInspectionTool
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

import static java.util.Collections.singletonList

@CompileStatic
class GrUncheckedAssignmentOfRawTypeTest extends GroovyLatestTest implements HighlightingTest {

  GrUncheckedAssignmentOfRawTypeTest() {
    super('highlighting')
  }

  @Override
  String getTestName() {
    return super.getTestName().capitalize()
  }

  final Collection<Class<? extends LocalInspectionTool>> inspections = singletonList(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection)

  @Test
  void rawMethodAccess() { fileHighlightingTest() }

  @Test
  void rawFieldAccess() { fileHighlightingTest() }

  @Test
  void rawArrayStyleAccess() { fileHighlightingTest() }

  @Test
  void rawArrayStyleAccessToMap() { fileHighlightingTest() }

  @Test
  void rawArrayStyleAccessToList() { fileHighlightingTest() }

  @Test
  void rawClosureReturnType() {
    highlightingTest '''\
class A<T> {
  A(T t) {this.t = t}

  T t
  def cl = {
    return t
  }
}


def a = new A(new Date())
Date d = <warning descr="Cannot assign 'Object' to 'Date'">a.cl()</warning>
'''
  }
}
