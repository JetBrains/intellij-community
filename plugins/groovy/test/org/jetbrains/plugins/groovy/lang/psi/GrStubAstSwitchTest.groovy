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
package org.jetbrains.plugins.groovy.lang.psi
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
/**
 * Created by Max Medvedev on 12/4/13
 */
class GrStubAstSwitchTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    null
  }

  void testDontLoadContentWhenProcessingImports() {
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("A.groovy", """
import java.util.concurrent.ConcurrentHashMap

class MyMap extends ConcurrentHashMap {}
class B extends ConcurrentHashMap {
  void foo() {
    print 4
  }
}
""")
    assert !file.contentsLoaded
    PsiClass bClass = file.classes[1]
    assert !file.contentsLoaded

    def fooMethod = bClass.methods[0]
    assert !file.contentsLoaded

    fooMethod.findDeepestSuperMethods()
    assert !file.contentsLoaded
  }

  void testDontLoadAstForAnnotation() {
    GroovyFileImpl file = myFixture.addFileToProject('a.groovy', '''\
class A {
  def foo(){}
}

class B {
  @Delegate
  A a = new A()
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazzB = file.classes[1]
    assert !file.contentsLoaded

    PsiField field = clazzB.fields[0]
    assert !file.contentsLoaded


    PsiModifierList modifierList = field.modifierList
    assert !file.contentsLoaded

    PsiAnnotation[] annotations = modifierList.annotations
    PsiAnnotation annotation = annotations[0]
    assert !file.contentsLoaded

    assert annotation.qualifiedName == 'groovy.lang.Delegate'
    assert !file.contentsLoaded
  }

  void testDontLoadAstForAnnotation2() {
    GroovyFileImpl file = myFixture.addFileToProject('a.groovy', '''\
class A {
  def foo(){}
}

class B extends A {
  @Override
  def foo() {}
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazzB = file.classes[1]
    assert !file.contentsLoaded

    PsiMethod method = clazzB.methods[0]
    assert !file.contentsLoaded


    PsiModifierList modifierList = method.modifierList
    assert !file.contentsLoaded

    PsiAnnotation[] annotations = modifierList.annotations
    PsiAnnotation annotation = annotations[0]
    assert !file.contentsLoaded

    assert annotation.qualifiedName == "java.lang.Override"
    assert !file.contentsLoaded
  }


  void testDelegateExists() {
    GroovyFileImpl file = myFixture.addFileToProject('a.groovy', '''\
class A {
  def foo(){}
}

class B {
  @Delegate
  A a = new A()
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazzB = file.classes[1]
    assert !file.contentsLoaded

    assert clazzB.methods.find {it.name =='foo'}
    assert !file.contentsLoaded
  }

  void testDefaultValueForAnnotation() {
    myFixture.addFileToProject('pack/Ann.groovy', '''\
package pack

@interface Ann {
    String foo() default 'def'
}
''')

    GroovyFileImpl file = myFixture.addFileToProject('usage.groovy', '''\
import pack.Ann

class X {
  @Ann()
  String bar() {}
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazz = file.classes[0]
    assert !file.contentsLoaded
    PsiMethod method = clazz.methods[0]
    assert !file.contentsLoaded
    PsiAnnotation annotation = method.modifierList.findAnnotation('pack.Ann')
    assert !file.contentsLoaded
    assert annotation.findAttributeValue('foo') != null
    assert !file.contentsLoaded
  }

  void testDefaultValueForAnnotationWithAliases() {
    myFixture.addFileToProject('pack/Ann.groovy', '''\
package pack

@interface Ann {
    String foo() default 'def'
}
''')

    GroovyFileImpl file = myFixture.addFileToProject('usage.groovy', '''\
import pack.Ann as A

class X {
  @A()
  String bar() {}
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazz = file.classes[0]
    assert !file.contentsLoaded
    PsiMethod method = clazz.methods[0]
    assert !file.contentsLoaded
    PsiAnnotation annotation = method.modifierList.findAnnotation('pack.Ann')
    assert !file.contentsLoaded
    assert annotation.findAttributeValue('foo') != null
    assert !file.contentsLoaded
  }

  void testValueForAnnotationWithAliases() {
    myFixture.addFileToProject('pack/Ann.groovy', '''\
package pack

@interface Ann {
    String foo() default 'def'
}
''')

    GroovyFileImpl file = myFixture.addFileToProject('usage.groovy', '''\
import pack.Ann as A

class X {
  @A(foo='non_def')
  String bar() {}
}
''') as GroovyFileImpl

    assert !file.contentsLoaded
    PsiClass clazz = file.classes[0]
    assert !file.contentsLoaded
    PsiMethod method = clazz.methods[0]
    assert !file.contentsLoaded
    PsiAnnotation annotation = method.modifierList.findAnnotation('pack.Ann')
    assert !file.contentsLoaded
    assert annotation.findAttributeValue('foo') != null
    assert file.contentsLoaded
  }

  public void "test do not load content for findMethodsByName"() {
    GroovyFileImpl file = myFixture.addFileToProject('usage.groovy', '''\
class X {
  void foo(int a, int b = 2) {}
}
''') as GroovyFileImpl
    assert !file.contentsLoaded
    PsiClass clazz = file.classes[0]
    assert !file.contentsLoaded
    
    assert clazz.findMethodsByName('foo', false).size() == 2
    assert !file.contentsLoaded
  }

}
