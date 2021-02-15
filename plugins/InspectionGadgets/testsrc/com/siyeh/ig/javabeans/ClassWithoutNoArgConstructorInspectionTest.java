// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassWithoutNoArgConstructorInspectionTest extends LightJavaInspectionTestCase {
  public void testSimple() {
    doTest("class /*'Bean' has no no-arg constructor*/Bean/**/ {Bean(int x) {}}\n");
  }

  public void testRecord() {
    doTest("record Bean(int x) {public Bean(int x) {this.x = x;}}\n");
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ClassWithoutNoArgConstructorInspection();
  }
}