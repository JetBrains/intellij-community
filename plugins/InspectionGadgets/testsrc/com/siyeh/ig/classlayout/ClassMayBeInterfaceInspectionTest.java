/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ClassMayBeInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testOne() {
    doTest("""
             abstract class /*Abstract class 'ConvertMe' may be interface*/ConvertMe/**/ {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  public void testOnTwo() {
    doTest("""
             class ConvertMe {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  public void testMethodCantBeDefault() {
    doTest("""
             class Issue {
                 public abstract class Inner {
                     public Issue getParent() {
                         return Issue.this;
                     }
                 }
             }""");
  }

  public void testObjectMethods() {
    doTest("""
             abstract class X {
               public boolean equals(Object o) { return false; }
               public int hashCode() { return 1; }
               public String toString() { return null; }
             }""");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    final ClassMayBeInterfaceInspection inspection = new ClassMayBeInterfaceInspection();
    inspection.reportClassesWithNonAbstractMethods = true;
    return inspection;
  }
}
