// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.configureByExactName;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class NamedParamsCompletionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void testWithSetter() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
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
    });
  }

  @Test
  public void testWithJava() {
    runInEdtAndWait(() -> {
      configureByExactName(groovy.get(), "A.java", """
      
        class A {
          public void setParam(int tt){}
        }
        """);
      completion.get().doCompletionTest("""
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
    });
  }

  @Test
  public void testWithSeveralVariants() {
    runInEdtAndWait(() -> {
      completion.get().doVariantableTest("""
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
    });
  }

  @Test
  public void testWithNamedParams() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           package com.foo;
                           public class MyCoolClass {}
                           """);
      completion.get().doVariantableTest("""
                          import groovy.transform.NamedParam
                          import groovy.transform.NamedVariant
                          import com.foo.MyCoolClass
                        
                          @NamedVariant
                          String foo(@NamedParam int param1, @NamedParam MyCoolClass param2) {
                             null
                          }
                        
                          foo(par<caret>)
                          """, CompletionType.BASIC, "param1", "param2");
    });
  }

  @Test
  public void testNamedParamsWithValue() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           package com.foo;
                           public class MyCoolClass {}
                           """);
      completion.get().doCompletionTest("""
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
    });
  }

}
