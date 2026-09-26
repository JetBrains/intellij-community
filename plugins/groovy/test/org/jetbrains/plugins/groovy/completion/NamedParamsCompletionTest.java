// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class NamedParamsCompletionTest extends GroovyCompletionTestBase {
  public void testWithSetter() {
    doCompletionTest("""
                       import groovy.transform.NamedDelegate
                       import groovy.transform.NamedVariant
                       
                       class E {
                           private int a
                           void setParam(int tt){}
                       }
                       
                       @NamedVariant
                       String foo(@NamedDelegate E e) {
                           null
                       }
                       
                       foo(par<caret>)
                       """, """
                       import groovy.transform.NamedDelegate
                       import groovy.transform.NamedVariant
                       
                       class E {
                           private int a
                           void setParam(int tt){}
                       }
                       
                       @NamedVariant
                       String foo(@NamedDelegate E e) {
                           null
                       }
                       
                       foo(param: <caret>)
                       """, CompletionType.BASIC);
  }

  public void testWithJava() {
    myFixture.configureByText("A.java", """
      
      class A {
        public void setParam(int tt){}
      }
      """);
    doCompletionTest("""
                       import groovy.transform.NamedDelegate
                       import groovy.transform.NamedVariant
                       
                       @NamedVariant
                       String foo(@NamedDelegate A a) {
                           null
                       }
                       
                       foo(par<caret>)
                       """, """
                       import groovy.transform.NamedDelegate
                       import groovy.transform.NamedVariant
                       
                       @NamedVariant
                       String foo(@NamedDelegate A a) {
                           null
                       }
                       
                       foo(param: <caret>)
                       """, CompletionType.BASIC);
  }

  public void testWithSeveralVariants() {
    doVariantableTest("""
                        import groovy.transform.NamedDelegate
                        import groovy.transform.NamedVariant
                        
                        class E {
                            int param1
                            void setParam2(int tt){}
                        }
                        
                        @NamedVariant
                        String foo(@NamedDelegate E e) {
                            null
                        }
                        
                        foo(par<caret>)
                        """, CompletionType.BASIC, "param1", "param2");
  }

  public void testWithNamedParams() {
    myFixture.addClass("""
                         package com.foo;
                         public class MyCoolClass {}
                         """);
    doVariantableTest("""
                        import groovy.transform.NamedParam
                        import groovy.transform.NamedVariant
                        import com.foo.MyCoolClass
                        
                        @NamedVariant
                        String foo(@NamedParam int param1, @NamedParam MyCoolClass param2) {
                           null
                        }
                        
                        foo(par<caret>)
                        """, CompletionType.BASIC, "param1", "param2");
  }

  public void testNamedParamsWithValue() {
    myFixture.addClass("""
                         package com.foo;
                         public class MyCoolClass {}
                         """);
    doCompletionTest("""
                       import groovy.transform.NamedParam
                       import groovy.transform.NamedVariant
                       import com.foo.MyCoolClass
                       
                       @NamedVariant
                       String foo(@NamedParam("larch1") int param1, @NamedParam("param2") MyCoolClass larch2) {
                           null
                       }
                       
                       foo(lar<caret>)
                       """, """
                       import groovy.transform.NamedParam
                       import groovy.transform.NamedVariant
                       import com.foo.MyCoolClass
                       
                       @NamedVariant
                       String foo(@NamedParam("larch1") int param1, @NamedParam("param2") MyCoolClass larch2) {
                           null
                       }
                       
                       foo(larch1: <caret>)
                       """, CompletionType.BASIC);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }
}
