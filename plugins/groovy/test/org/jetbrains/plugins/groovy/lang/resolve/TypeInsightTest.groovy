// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

@CompileStatic
class TypeInsightTest extends GroovyLatestTest implements TypingTest {

  @Test
  void 'map iterator'() {
    typingTest 'def usage(Map<String, Integer> m) { <caret>m.iterator() }', GrMethodCall,
               'java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>'
  }

  @Test
  void 'map iterator explicit'() {
    typingTest 'def usage(Map<String, Integer> m) { <caret>org.codehaus.groovy.runtime.DefaultGroovyMethods.iterator(m) }', GrMethodCall,
               'java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>'
  }
}
