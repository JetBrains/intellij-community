// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GrAutoCloneTransformationSupportTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

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
    new Pogo().<error descr="Unhandled exception: java.lang.CloneNotSupportedException">clone</error>();
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
    <error descr="Unhandled exception: java.lang.CloneNotSupportedException">cloneOrCopyMembers</error>(pogo);
    cloneOrCopyMembers<error descr="'cloneOrCopyMembers(Pogo)' in 'Pogo' cannot be applied to '()'">()</error>;
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
