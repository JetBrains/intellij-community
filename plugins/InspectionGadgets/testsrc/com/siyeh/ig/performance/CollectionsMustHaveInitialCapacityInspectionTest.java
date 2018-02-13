/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CollectionsMustHaveInitialCapacityInspectionTest extends LightInspectionTestCase {
  private final CollectionsMustHaveInitialCapacityInspection myInspection = new CollectionsMustHaveInitialCapacityInspection();

  @Override
  protected InspectionProfileEntry getInspection() {
    return myInspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.concurrent;" +
      "public class ConcurrentHashMap {}",
      "package java.util;" +
      "public class WeakHashMap {" +
      "  public WeakHashMap() {}" +
      "  public WeakHashMap(int c) {}" +
      "}",
      "package java.util;" +
      "public class HashSet {" +
      "  public HashSet() {}" +
      "  public HashSet(int c) {}" +
      "}",
      "package java.util;" +
      "public class BitSet {" +
      "  public BitSet() {}" +
      "  public BitSet(int c) {}" +
      "}",
      "package java.util;" +
      "public class Vector {" +
      "  public Vector() {}" +
      "  public Vector(int c) {}" +
      "}"
    };
  }

  public void testSimple() {
    doStatementTest("new /*'new java.util.concurrent.ConcurrentHashMap()' without initial capacity*/java.util.concurrent.ConcurrentHashMap/**/();");
  }

  public void testMore() {
    doTest("import java.util.*;" +
           "class X {" +
           "  void m() {" +
           "    new /*'new HashMap<String, String>()' without initial capacity*/HashMap<String, String>/**/();" +
           "    new HashMap<String, String>(3);" +
           "    new /*'new HashMap()' without initial capacity*/HashMap/**/();" +
           "    new HashMap(3);" +
           "    new /*'new WeakHashMap()' without initial capacity*/WeakHashMap/**/();" +
           "    new WeakHashMap(3);" +
           "    new /*'new HashSet()' without initial capacity*/HashSet/**/();" +
           "    new HashSet(3);" +
           "    new /*'new Hashtable()' without initial capacity*/Hashtable/**/();" +
           "    new Hashtable(3);" +
           "    new /*'new BitSet()' without initial capacity*/BitSet/**/();" +
           "    new BitSet(3);" +
           "    new /*'new Vector()' without initial capacity*/Vector/**/();" +
           "    new Vector(3);" +
           "    new /*'new ArrayList()' without initial capacity*/ArrayList/**/();" +
           "    new ArrayList(3);" +
           "  }" +
           "}");
  }

  public void testFieldsReported() {
    doTest("import java.util.*;" +
           "class X {" +
           "   HashMap m1 = new /*'new HashMap<String, String>()' without initial capacity*/HashMap<String, String>/**/();" +
           "   HashMap m2 = new HashMap<String, String>(3);" +
           "}");
  }

  public void testFieldsNotReported() {
    myInspection.myIgnoreFields = true;
    try {
      doTest("import java.util.*;" +
             "class X {" +
             "   HashMap m1 = new HashMap<String, String>();" +
             "   HashMap m2 = new HashMap<String, String>(3);" +
             "}");
    }
    finally {
      myInspection.myIgnoreFields = false;
    }
  }
}
