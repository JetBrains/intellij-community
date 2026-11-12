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
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrCreateFieldFromUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Create field", null, GrUnresolvedAccessInspection.class);

  @Test
  public void testSimpleRef() {
    grIntention.get().doTextTest("""
                 class A {
                     def foo() {
                         print obje<caret>ct
                     }
                 }
                 """, """
                 class A {
                     def object
                 
                     def foo() {
                         print object
                     }
                 }
                 """);
  }

  @Test
  public void testThisRef() {
    grIntention.get().doTextTest("""
                 class A {
                     def foo() {
                         print this.obje<caret>ct
                     }
                 }
                 """, """
                 class A {
                     def object
                 
                     def foo() {
                         print this.object
                     }
                 }
                 """);
  }

  @Test
  public void testInStaticMethod() {
    grIntention.get().doTextTest("""
                 class A {
                     static def foo() {
                         print obje<caret>ct
                     }
                 }
                 """, """
                 class A {
                     static def object
                 
                     static def foo() {
                         print object
                     }
                 }
                 """);
  }

  @Test
  public void testInStaticMethodwithThis() {
    grIntention.get().doTextTest("""
                 class A {
                     static def foo() {
                         print this.obje<caret>ct
                     }
                 }
                 """, """
                 class A {
                     static def object
                 
                     static def foo() {
                         print this.object
                     }
                 }
                 """);
  }

  @Test
  public void testQualifiedRef() {
    grIntention.get().doTextTest("""
                 class A {
                 }
                 print new A().obje<caret>ct
                 """, """
                 class A {
                     def object
                 }
                 print new A().object
                 """);
  }

  @Test
  public void testQualifiedStaticRef() {
    grIntention.get().doTextTest("""
                 class A {
                 }
                 print A.obje<caret>ct
                 """, """
                 class A {
                     static def object
                 }
                 print A.object
                 """);
  }

  @Test
  public void testClassRef() {
    grIntention.get().doTextTest("""
                 class A {
                 }
                 print A.class.obj<caret>ect
                 """, """
                 class A {
                     static def object
                 }
                 print A.class.object
                 """);
  }
}
