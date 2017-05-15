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
package org.jetbrains.plugins.groovy.transformations

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass

@CompileStatic
class BaseScriptTransformationSupportTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
