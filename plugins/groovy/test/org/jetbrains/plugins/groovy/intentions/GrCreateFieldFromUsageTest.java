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

import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

/**
 * @author Max Medvedev
 */
public class GrCreateFieldFromUsageTest extends GrIntentionTestCase {
  public GrCreateFieldFromUsageTest() {
    super("Create field", GrUnresolvedAccessInspection.class);
  }

  public void testSimpleRef() {
    doTextTest("""
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

  public void testThisRef() {
    doTextTest("""
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

  public void testInStaticMethod() {
    doTextTest("""
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

  public void testInStaticMethodwithThis() {
    doTextTest("""
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

  public void testQualifiedRef() {
    doTextTest("""
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

  public void testQualifiedStaticRef() {
    doTextTest("""
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

  public void testClassRef() {
    doTextTest("""
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
