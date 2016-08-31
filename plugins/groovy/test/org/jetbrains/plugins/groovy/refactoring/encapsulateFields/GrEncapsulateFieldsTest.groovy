/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.encapsulateFields

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PropertyUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor
import com.intellij.refactoring.encapsulateFields.FieldDescriptor
import com.intellij.refactoring.encapsulateFields.FieldDescriptorImpl
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Max Medvedev
 */
class GrEncapsulateFieldsTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + 'groovy/refactoring/encapsulateFields/'
  }

  void testSimple() {
    doJavaTest('''\
def a = new A()
a.foo = 2
print a.foo
a.foo += 1
a.foo ++
''', '''\
def a = new A()
a.foo = 2
print a.foo
a.foo += 1
a.foo ++
''', true, true, 'getFoo', 'setFoo', true)
  }

  void testDifferentGetter() {
    doJavaTest('''\
def a = new A()
a.foo = 2
print a.foo
a.foo += 1
a.foo ++
''', '''\
def a = new A()
a.foo = 2
print a.bar
a.foo = a.bar + 1
a.foo = a.bar + 1
''', true, true, 'getBar', 'setFoo', true)
  }

  void testDifferentSetter() {
    doJavaTest('''\
def a = new A()
a.foo = 2
print a.foo
a.foo += 1
a.foo ++
''', '''\
def a = new A()
a.bar = 2
print a.foo
a.bar = a.foo + 1
a.bar = a.foo + 1
''', true, true, 'getFoo', 'setBar', true)
  }

  void testNonAccessors() {
    doJavaTest('''\
def a = new A()
a.foo = 2
print a.foo
a.foo += 1
a.foo ++
''', '''\
def a = new A()
a.baz(2)
print a.bar()
a.baz(a.bar() + 1)
a.baz(a.bar() + 1)
''', true, true, 'bar', 'baz', true)
  }

  void testInheritor() {
    doJavaTest('''\
class Inheritor extends A {
    def abc() {
        foo = 2
        print foo
        foo += 1
        foo ++
    }
}
''', '''\
class Inheritor extends A {
    def abc() {
        baz(2)
        print bar()
        baz(bar() + 1)
        baz(bar() + 1)
    }
}
''', true, true, 'bar', 'baz', true)
  }

  void testFieldUsageInClassInheritor() {
    doJavaTest('''\
class Inheritor extends A {
    def abc() {
        foo = 2
        print foo
        foo += 1
        foo ++
    }
}
''', '''\
class Inheritor extends A {
    def abc() {
        foo = 2
        print foo
        foo += 1
        foo ++
    }
}
''', true, true, 'bar', 'baz', false)
  }

  void testFieldUsageInClassInheritor2() {
    doJavaTest('''\
class Inheritor extends A {
    def abc() {
        foo = 2
        print foo
        foo += 1
        foo ++
    }
}
''', '''\
class Inheritor extends A {
    def abc() {
        this.@foo = 2
        print this.@foo
        this.@foo += 1
        this.@foo ++
    }
}
''', true, true, 'getFoo', 'setFoo', false)
  }

  void testInnerClass() {
    doJavaTest('''\
class X extends A {
    def bar() {
        new Runnable() {
            void run() {
                print foo
            }
        }
    }
}
''', '''\
class X extends A {
    def bar() {
        new Runnable() {
            void run() {
                print X.this.@foo
            }
        }
    }
}
''', null, true, true, 'getFoo', 'setFoo', false)
  }

  private void doJavaTest(String before,
                          String after,
                          String conflicts = null,
                          boolean generateGetters,
                          boolean generateSetters,
                          String getterName,
                          String setterName,
                          boolean toUseAccessorsWhenAccessible) {
    myFixture.addClass('''\
public class A {
  public int foo = 1;
}
''')

    myFixture.configureByText('a.groovy', before)
    doTest('A', 'foo', conflicts, generateGetters, generateSetters, getterName, setterName, toUseAccessorsWhenAccessible)
    myFixture.checkResult(after)
  }

  void doTest(String clazz,
              String field,
              String conflicts = null,
              boolean generateGetters,
              boolean generateSetters,
              String getterName,
              String setterName,
              boolean toUseAccessorsWhenAccessible) {
    PsiClass aClass = myFixture.getJavaFacade().findClass(clazz, GlobalSearchScope.projectScope(myFixture.project))
    PsiField aField = aClass.findFieldByName(field, false)


    final Project project = myFixture.getProject()

    if (!getterName) getterName = PropertyUtil.suggestGetterName(aField)
    if (!setterName) setterName = PropertyUtil.suggestSetterName(aField)

    try {
      final EncapsulateFieldsDescriptor descriptor = createMockDescriptor(aClass, aField, generateGetters, generateSetters, getterName,
                                                                          setterName, toUseAccessorsWhenAccessible)
      EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, descriptor)
      processor.run()
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        assertEquals(conflicts, e.getMessage())
        return
      }
      else {
        e.printStackTrace()
        fail(e.getMessage())
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts)
    }
  }

  static EncapsulateFieldsDescriptor createMockDescriptor(PsiClass aClass,
                                                          PsiField field,
                                                          boolean generateGetters,
                                                          boolean generateSetters,
                                                          String getterName,
                                                          String setterName,
                                                          boolean toUseAccessorsWhenAccessible) {
    return new EncapsulateFieldsDescriptor() {
      @Override
      FieldDescriptor[] getSelectedFields() {
        final FieldDescriptorImpl descriptor = new FieldDescriptorImpl(
          field,
          getterName,
          setterName,
          isToEncapsulateGet() ? ((PsiMethod)PropertyUtil.generateGetterPrototype(field).setName(getterName)) : null,
          isToEncapsulateSet() ? ((PsiMethod)PropertyUtil.generateSetterPrototype(field).setName(setterName)) : null
        )

        return [descriptor]
      }

      @Override
      boolean isToEncapsulateGet() {
        generateGetters
      }

      @Override
      boolean isToEncapsulateSet() {
        generateSetters
      }

      @Override
      boolean isToUseAccessorsWhenAccessible() {
        toUseAccessorsWhenAccessible
      }

      @Override
      String getFieldsVisibility() {
        toUseAccessorsWhenAccessible ? null : PsiModifier.PROTECTED
      }

      @Override
      String getAccessorsVisibility() {
        PsiModifier.PUBLIC
      }

      @Override
      int getJavadocPolicy() {
        DocCommentPolicy.MOVE
      }

      @Override
      PsiClass getTargetClass() {
        aClass
      }
    }
  }
}
