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

import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GrAutoCloneTransformationSupportTest extends LightGroovyTestCase {

  GroovyLightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test clone() return type'() {
    doExpressionTypeTest '''\
@groovy.transform.AutoClone
class A1 {}
new A1().clo<caret>ne()
''', 'A1'
  }

  void 'test clone() return type overridden'() {
    doExpressionTypeTest '''\
@groovy.transform.AutoClone
class A1 {}
@groovy.transform.AutoClone
class A2 extends A1 {}

new A2().clo<caret>ne()
''', 'A2'
  }

  void 'test clone() usage from java'() {
    myFixture.with {
      addFileToProject 'Pogo.groovy', '''\
@groovy.transform.AutoClone
class Pogo {} 
'''
      configureByText 'Main.java', '''\
class Main {
  void foo() {
    new Pogo().<error descr="Unhandled exception: java.lang.CloneNotSupportedException">clone();</error>;
    try {
       Pogo pogo = new Pogo().clone();
    } catch (java.lang.CloneNotSupportedException e) {}
  }
}
'''
      checkHighlighting()
    }
  }

  void 'test copy constructor usage from java'() {
    myFixture.with {
      addFileToProject 'Pogo.groovy', '''\
@groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.COPY_CONSTRUCTOR)
class Pogo {} 
'''
      configureByText 'Main.java', '''\
class Main {
  void foo() {
    Pogo a = new Pogo();
    Pogo b = new Pogo(a);
  }
}
'''
      checkHighlighting()
    }
  }

  void 'test cloneOrCopyMembers() usage from java'() {
    myFixture.with {
      addFileToProject 'Pogo.groovy', '''\
@groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.SIMPLE)
class Pogo {} 
'''
      configureByText 'Pojo.java', '''\
class Pojo extends Pogo {
  void foo() {
    Pogo pogo = new Pogo(); 
    <error descr="Unhandled exception: java.lang.CloneNotSupportedException">cloneOrCopyMembers(pogo);</error>
    cloneOrCopyMembers<error descr="'cloneOrCopyMembers(Pogo)' in '' cannot be applied to '()'">()</error>;
  }
}
'''
      checkHighlighting()
    }
  }

  private void doExpressionTypeTest(String text, String expectedType) {
    def file = myFixture.configureByText('_.groovy', text)
    def ref = file.findReferenceAt(myFixture.editor.caretModel.offset) as GrReferenceExpression
    def actual = ref.type
    assertType(expectedType, actual)
  }
}
