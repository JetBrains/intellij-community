// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.encapsulateFields;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptorImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GrEncapsulateFieldsTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/encapsulateFields/";
  }

  public void testSimple() {
    doJavaTest("""
                 def a = new A()
                 a.foo = 2
                 print a.foo
                 a.foo += 1
                 a.foo ++
                 """, """
                 def a = new A()
                 a.foo = 2
                 print a.foo
                 a.foo += 1
                 a.foo ++
                 """, "getFoo", "setFoo", true);
  }

  public void testDifferentGetter() {
    doJavaTest("""
                 def a = new A()
                 a.foo = 2
                 print a.foo
                 a.foo += 1
                 a.foo ++
                 """, """
                 def a = new A()
                 a.foo = 2
                 print a.bar
                 a.foo = a.bar + 1
                 a.foo = a.bar + 1
                 """, "getBar", "setFoo", true);
  }

  public void testDifferentSetter() {
    doJavaTest("""
                 def a = new A()
                 a.foo = 2
                 print a.foo
                 a.foo += 1
                 a.foo ++
                 """, """
                 def a = new A()
                 a.bar = 2
                 print a.foo
                 a.bar = a.foo + 1
                 a.bar = a.foo + 1
                 """, "getFoo", "setBar", true);
  }

  public void testNonAccessors() {
    doJavaTest("""
                 def a = new A()
                 a.foo = 2
                 print a.foo
                 a.foo += 1
                 a.foo ++
                 """, """
                 def a = new A()
                 a.baz(2)
                 print a.bar()
                 a.baz(a.bar() + 1)
                 a.baz(a.bar() + 1)
                 """, "bar", "baz", true);
  }

  public void testInheritor() {
    doJavaTest("""
                 class Inheritor extends A {
                     def abc() {
                         foo = 2
                         print foo
                         foo += 1
                         foo ++
                     }
                 }
                 """, """
                 class Inheritor extends A {
                     def abc() {
                         baz(2)
                         print bar()
                         baz(bar() + 1)
                         baz(bar() + 1)
                     }
                 }
                 """, "bar", "baz", true);
  }

  public void testFieldUsageInClassInheritor() {
    doJavaTest("""
                 class Inheritor extends A {
                     def abc() {
                         foo = 2
                         print foo
                         foo += 1
                         foo ++
                     }
                 }
                 """, """
                 class Inheritor extends A {
                     def abc() {
                         foo = 2
                         print foo
                         foo += 1
                         foo ++
                     }
                 }
                 """, "bar", "baz", false);
  }

  public void testFieldUsageInClassInheritor2() {
    doJavaTest("""
                 class Inheritor extends A {
                     def abc() {
                         foo = 2
                         print foo
                         foo += 1
                         foo ++
                     }
                 }
                 """, """
                 class Inheritor extends A {
                     def abc() {
                         this.@foo = 2
                         print this.@foo
                         this.@foo += 1
                         this.@foo ++
                     }
                 }
                 """, "getFoo", "setFoo", false);
  }

  public void testInnerClass() {
    doJavaTest("""
                 class X extends A {
                     def bar() {
                         new Runnable() {
                             void run() {
                                 print foo
                             }
                         }
                     }
                 }
                 """, """
                 class X extends A {
                     def bar() {
                         new Runnable() {
                             void run() {
                                 print X.this.@foo
                             }
                         }
                     }
                 }
                 """, "getFoo", "setFoo", false);
  }

  private void doJavaTest(String before,
                          String after,
                          String getterName,
                          String setterName,
                          boolean toUseAccessorsWhenAccessible) {
    myFixture.addClass("""
      public class A {
        public int foo = 1;
      }""");
    myFixture.configureByText("a.groovy", before);
    doTest("A", "foo", null, true, true, getterName, setterName, toUseAccessorsWhenAccessible);
    myFixture.checkResult(after);
  }

  public void doTest(String clazz,
                     String field,
                     String conflicts,
                     boolean generateGetters,
                     boolean generateSetters,
                     String getterName,
                     String setterName,
                     boolean toUseAccessorsWhenAccessible) {
    PsiClass aClass = myFixture.getJavaFacade().findClass(clazz, GlobalSearchScope.projectScope(myFixture.getProject()));
    PsiField aField = aClass.findFieldByName(field, false);


    final Project project = myFixture.getProject();

    if (!StringGroovyMethods.asBoolean(getterName)) getterName = PropertyUtilBase.suggestGetterName(aField);
    if (!StringGroovyMethods.asBoolean(setterName)) setterName = PropertyUtilBase.suggestSetterName(aField);

    try {
      final EncapsulateFieldsDescriptor descriptor =
        createMockDescriptor(aClass, aField, generateGetters, generateSetters, getterName, setterName, toUseAccessorsWhenAccessible);
      EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, descriptor);
      processor.run();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        TestCase.assertEquals(conflicts, e.getMessage());
        return;
      }
      else {
        e.printStackTrace();
        TestCase.fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      TestCase.fail("Conflicts were not detected: " + conflicts);
    }
  }

  public void doTest(String clazz,
                     String field,
                     boolean generateGetters,
                     boolean generateSetters,
                     String getterName,
                     String setterName,
                     boolean toUseAccessorsWhenAccessible) {
    doTest(clazz, field, null, generateGetters, generateSetters, getterName, setterName, toUseAccessorsWhenAccessible);
  }

  public static EncapsulateFieldsDescriptor createMockDescriptor(final PsiClass aClass,
                                                                 final PsiField field,
                                                                 final boolean generateGetters,
                                                                 final boolean generateSetters,
                                                                 final String getterName,
                                                                 final String setterName,
                                                                 final boolean toUseAccessorsWhenAccessible) {
    return new EncapsulateFieldsDescriptor() {
      @Override
      public FieldDescriptor[] getSelectedFields() {
        final FieldDescriptorImpl descriptor = new FieldDescriptorImpl(
          field, getterName, setterName,
          isToEncapsulateGet() ? ((PsiMethod)PropertyUtilBase.generateGetterPrototype(field).setName(getterName)) : null,
          isToEncapsulateSet() ? ((PsiMethod)PropertyUtilBase.generateSetterPrototype(field).setName(setterName)) : null);
        return new FieldDescriptorImpl[]{descriptor};
      }

      @Override
      public boolean isToEncapsulateGet() {
        return generateGetters;
      }

      @Override
      public boolean isToEncapsulateSet() {
        return generateSetters;
      }

      @Override
      public boolean isToUseAccessorsWhenAccessible() {
        return toUseAccessorsWhenAccessible;
      }

      @Override
      public String getFieldsVisibility() {
        return toUseAccessorsWhenAccessible ? null : PsiModifier.PROTECTED;
      }

      @Override
      public String getAccessorsVisibility() {
        return PsiModifier.PUBLIC;
      }

      @Override
      public int getJavadocPolicy() {
        return DocCommentPolicy.MOVE;
      }

      @Override
      public PsiClass getTargetClass() {
        return aClass;
      }
    };
  }
}
