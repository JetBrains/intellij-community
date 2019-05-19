// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class ResolvePropertyVersusTest extends GroovyLatestTest implements ResolveTest {

  @Test
  void 'instance getter vs instance field'() {
    fixture.addFileToProject 'classes.groovy', '''\
class C {
  public foo = "field"
  def getFoo() { "getter" }
}
'''
    resolveTest 'new C().<caret>foo', GrMethod
    resolveTest 'C.<caret>foo', GrField
  }

  @Test
  void 'static getter vs static field'() {
    fixture.addFileToProject 'classes.groovy', '''\
class C {
  public static foo = "field"
  static def getFoo() { "getter" }
}
'''
    resolveTest 'new C().<caret>foo', GrMethod
    resolveTest 'C.<caret>foo', GrMethod
  }

  @Test
  void 'instance getter vs static field'() {
    fixture.addFileToProject 'classes.groovy', '''\
class C {
  public static foo = "field"
  def getFoo() { "getter" }
}
'''
    resolveTest 'new C().<caret>foo', GrMethod
    resolveTest 'C.<caret>foo', GrField
  }

  @Test
  void 'static getter vs instance field'() {
    fixture.addFileToProject 'classes.groovy', '''\
class C {
  public foo = "field"
  static def getFoo() { "getter" }
}
'''
    resolveTest 'new C().<caret>foo', GrMethod
    resolveTest 'C.<caret>foo', GrMethod
  }
}
