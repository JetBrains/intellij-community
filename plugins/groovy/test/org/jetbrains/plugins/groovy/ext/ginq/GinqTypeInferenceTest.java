// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

@SuppressWarnings("SpellCheckingInspection")
@TestApplication
public class GinqTypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GinqTestUtils.getProjectDescriptor());
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testGQL() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from a in [1]
                     select a
                 }
             e<caret>e
             """, "java.util.List<java.lang.Integer>");
  }

  @Test
  public void testNamedRecord() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from a in [1]
                     select a, a
                 }
             e<caret>e
             """, "java.util.List<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>");
  }

  @Test
  public void testActualBinding() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from aa in [1]
                     select aa as e1, aa as e2
                 }
             ee[0].e<caret>1
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testDataSourceBinding() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from aa in [1]
                     select aa as e1, aa as e2
                 }
             ee[0].a<caret>a
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testJoinSourceBinding() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from aa in [1]
                     crossjoin bb in [""]
                     select aa as e1, bb as e2
                 }
             ee[0].b<caret>b
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testGetAt1() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from aa in [1]
                     crossjoin bb in [""]
                     select aa as e1, bb as e2
                 }
             ee[0].ge<caret>t(0)
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testGetAt2() {
    inference.get().doTest("""
             
             def ee = GQL {
                     from aa in [1]
                     crossjoin bb in [""]
                     select aa as e1, bb as e2
                 }
             ee[0].ge<caret>t(1)
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testNestedGinq() {
    inference.get().doTest("""
             
             GQ {
               from v in (
                 from nn in [1, 2, 3]
                 select nn, Math.pow(n, 2) as powerOfN
               )
               select v.n<caret>n, v.powerOfN
             }""", JAVA_LANG_INTEGER);
  }

  @Test
  public void testPrimitive() {
    inference.get().doTest("""
             
             def aa = GQL {
                     from x in [1]
                     select x.byteValue()
                 }
             a<caret>a""", "java.util.List<java.lang.Byte>");
  }

  @Test
  public void testOver() {
    inference.get().doTest("""
             
             def aa = GQL {
                     from nnnn in [3]
                     select (rowNumber() over(orderby nnnn))
                 }
             a<caret>a""", "java.util.List<java.lang.Long>");
  }

  @Test
  public void testMethodNestedGinq() {
    inference.get().doTest("""
             
             import groovy.ginq.transform.GQ
             
             @GQ
             def foo() {
               from v in (
                 from nn in [1, 2, 3]
                 select nn, Math.pow(n, 2) as powerOfN
               )
               select v.n<caret>n, v.powerOfN
             }""", JAVA_LANG_INTEGER);
  }

  @Test
  public void testMethodReturnType1() {
    inference.get().doTest("""
             
             import groovy.ginq.transform.GQ
             
             @GQ
             def foo() {
               from v in (
                 from nn in [1, 2, 3]
                 select nn, Math.pow(n, 2) as powerOfN
               )
               select v.nn, v.powerOfN
             }
             
             def zz = foo()
             z<caret>z""",
           "org.apache.groovy.ginq.provider.collection.runtime.Queryable<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>");
  }

  @Test
  public void testMethodReturnType2() {
    inference.get().doTest("""
             
             import groovy.ginq.transform.GQ
             
             @GQ(List)
             def foo() {
               from v in (
                 from nn in [1, 2, 3]
                 select nn, Math.pow(n, 2) as powerOfN
               )
               select v.nn, v.powerOfN
             }
             
             def zz = foo()
             z<caret>z""", "java.util.List<org.apache.groovy.ginq.provider.collection.runtime.NamedRecord>");
  }

  @Test
  public void testMethodReturnType3() {
    inference.get().doTest("""
             
             import groovy.ginq.transform.GQ
             
             @GQ(List)
             def foo() {
               from v in (
                 from nn in [1, 2, 3]
                 select nn, Math.pow(n, 2) as powerOfN
               )
               select v.nn as rr, v.powerOfN
             }
             
             def zz = foo()
             zz[0].r<caret>r""", JAVA_LANG_INTEGER);
  }
}
