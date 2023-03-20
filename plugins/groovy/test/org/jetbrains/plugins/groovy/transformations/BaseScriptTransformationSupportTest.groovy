// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class BaseScriptTransformationSupportTest extends GroovyLatestTest implements ResolveTest {

  private void doStubTest(String text, String packageName = null) {
    fixture.addFileToProject 'script/base.groovy', 'package script; abstract class MyBaseScript extends Script {}'
    def file = fixture.addFileToProject('Zzz.groovy', text) as GroovyFileImpl
    assert !file.contentsLoaded

    def clazz = fixture.findClass(packageName == null ? 'Zzz' : (String)"${packageName}.Zzz")
    assert clazz instanceof GroovyScriptClass
    assert !file.contentsLoaded

    assert InheritanceUtil.isInheritor(clazz as PsiClass, 'script.MyBaseScript')
    assert !file.contentsLoaded
  }

  @Test
  void 'top level'() {
    doStubTest '@groovy.transform.BaseScript script.MyBaseScript hello'
  }

  @Test
  void 'script block level'() {
    doStubTest 'if (true) @groovy.transform.BaseScript script.MyBaseScript hello'
  }

  @Test
  void 'within method'() {
    doStubTest '''\
def foo() {
  @groovy.transform.BaseScript script.MyBaseScript hello  
}
'''
  }

  @Test
  void 'on import'() {
    doStubTest '''\
@BaseScript(script.MyBaseScript)
import groovy.transform.BaseScript
'''
  }

  @Test
  void 'on package'() {
    doStubTest '''\
@groovy.transform.BaseScript(script.MyBaseScript)
package com.foo
''', 'com.foo'
  }

  @Test
  void 'no AE when script class has same name as a package'() {
    fixture.with {
      addClass '''\
package root.foo;
public abstract class Bar extends groovy.lang.Script {}
'''
      configureByText 'root.groovy', '''\
import root.foo.Bar
@groovy.transform.BaseScript Bar dsl
'''
      checkHighlighting()
    }
  }

  @Test
  void 'resolve to base class getter'() {
    fixture.addFileToProject 'classes.groovy', '''\
abstract class BaseClass extends Script {
    int getStuffFromBaseClass() { 42 }
}
'''
    resolveTest '''\
@groovy.transform.BaseScript BaseClass script
<caret>stuffFromBaseClass
''', GrMethod
  }

  @Test
  void 'circularInheritance'() {
    fixture.addFileToProject 'Util.groovy', '''\
abstract class Util extends Script {}
'''
    fixture.configureByText 'Script.groovy', '''\
<error>@groovy.transform.BaseScript</error> Util script
'''
    fixture.checkHighlighting()
  }
}
