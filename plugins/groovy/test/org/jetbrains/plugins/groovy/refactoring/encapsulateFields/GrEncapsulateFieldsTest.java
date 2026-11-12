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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrEncapsulateFieldsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/encapsulateFields/");

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      public class A {
        public int foo = 1;
      }""");
    myFixture.configureByText("a.groovy", before);
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest("A", "foo", null, true, true, getterName, setterName, toUseAccessorsWhenAccessible);
      myFixture.checkResult(after);
    });
  }

  public void doTest(String clazz,
                     String field,
                     String conflicts,
                     boolean generateGetters,
                     boolean generateSetters,
                     String getterName,
                     String setterName,
                     boolean toUseAccessorsWhenAccessible) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
        assertEquals(conflicts, e.getMessage());
        return;
      }
      else {
        e.printStackTrace();
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
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
