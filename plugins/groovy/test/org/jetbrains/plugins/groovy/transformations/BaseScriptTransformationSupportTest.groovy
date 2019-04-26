// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass

@CompileStatic
class BaseScriptTransformationSupportTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  private void doTest(String text) {
    fixture.addFileToProject 'base.groovy', 'abstract class MyBaseScript extends Script {}'
    def file = fixture.addFileToProject('Zzz.groovy', """\
import groovy.transform.BaseScript

$text
""") as GroovyFileImpl
    assert !file.contentsLoaded

    def clazz = fixture.findClass('Zzz')
    assert clazz instanceof GroovyScriptClass
    assert !file.contentsLoaded

    assert InheritanceUtil.isInheritor(clazz as PsiClass, 'MyBaseScript')
    assert !file.contentsLoaded
  }

  void 'test top level'() {
    doTest '@BaseScript MyBaseScript hello'
  }

  void 'test script block level'() {
    doTest 'if (true) @BaseScript MyBaseScript hello'
  }

  void 'test within method'() {
    doTest '''\
def foo() {
  @BaseScript MyBaseScript hello  
}
'''
  }

  void 'test no AE when script class has same name as a package'() {
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
}
