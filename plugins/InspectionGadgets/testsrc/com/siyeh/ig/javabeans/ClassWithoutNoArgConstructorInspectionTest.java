// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassWithoutNoArgConstructorInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class /*Class 'Bean' is missing a no-arg constructor*/Bean/**/ {Bean(int x) {}}\n");
  }

  public void testRecord() {
    doTest("record Bean(int x) {public Bean(int x) {this.x = x;}}\n");
  }

  public void testAnonymousClass() {
    doTest("class X {" +
           "  X() {}" +
           "  X(String s) {}" +
           "  void x() {" +
           "    new X(\"asdf\") {{" +
           "      System.out.println();" +
           "    }};" +
           "  }" +
           "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    final ClassWithoutNoArgConstructorInspection inspection = new ClassWithoutNoArgConstructorInspection();
    inspection.m_ignoreClassesWithNoConstructors = false;
    return inspection;
  }
}