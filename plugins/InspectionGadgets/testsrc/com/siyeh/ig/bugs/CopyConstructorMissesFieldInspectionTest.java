// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CopyConstructorMissesFieldInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class Simple {" +
           "  private String name;" +
           "  public /*Copy constructor does not copy field 'name'*/Simple/**/(Simple simple) {}" +
           "}");
  }

  public void testTwo() {
    doTest("class X {" +
           "  int one, two;" +
           "  /*Copy constructor does not copy fields 'one' and 'two'*/X/**/(X x) {}" +
           "}");
  }

  public void testThree() {
    doTest("class X {" +
           "  int one, two, three;" +
           "  /*Copy constructor does not copy fields 'one', 'two' and 'three'*/X/**/(X x) {}" +
           "}");
  }

  public void testFour() {
    doTest("class X {" +
           "  int one, two, three, four;" +
           "  /*Copy constructor does not copy 4 fields*/X/**/(X x) {}" +
           "}");
  }

  public void testSimpleAssignment() {
    doTest("class X {" +
           "  int one, two;" +
           "  X(X x) {" +
           "    (this).one = x.one;" +
           "    two = x.two;" +
           "  }" +
           "}");
  }

  public void testSimpleThisCall() {
    doTest("class X {" +
           "  int one, two;" +
           "  X(int one, int two) {" +
           "    this.one = one;" +
           "    this.two = two;" +
           "  }" +
           "  X(X x) {" +
           "    this(x.one, x.two);" +
           "  }" +
           "}");
  }

  public void testGetterThisCall() {
    doTest("class X {" +
           "  int one, two;" +
           "  X(int one, int two) {" +
           "    this.one = one;" +
           "    this.two = two;" +
           "  }" +
           "  X(X x) {" +
           "    this(x.getOne(), x.getTwo());" +
           "  }" +
           "  int getOne() {" +
           "    return one;" +
           "  }" +
           "  int getTwo() {" +
           "    return two;" +
           "  }" +
           "}");
  }

  public void testGettersAndSetters() {
    doTest("class X {" +
           "  int one, two;" +
           "  X(X x) {" +
           "    setOne(x.getOne());" +
           "    setTwo(x.getTwo());" +
           "  }" +
           "  int getOne() { return one; }" +
           "  void setOne(int one) { this.one = one; }" +
           "  int getTwo() { return two; }" +
           "  void setTwo(int two) { this.two = two; }" +
           "}");
  }

  public void testTransient() {
    doTest("class X {" +
           "  transient int one;" +
           "  X(X x) {" +
           "    this();" +
           "  }" +
           "  X() {}" +
           "}");
  }

  public void testMethodCalled() {
    doTest("import java.util.*;" +
           "class X {" +
           "  private List<String> list = new ArrayList<>();" +
           "  X(X x) {" +
           "    list.addAll(x.list);" +
           "  }" +
           "}");
  }

  public void testContainsOwnCopy() {
    doTest("class X {" +
           "  int one, two;" +
           "  X copy;" +
           "  X(X x) {" +
           "    copy = x;" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CopyConstructorMissesFieldInspection();
  }
}