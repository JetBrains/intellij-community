// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ThrowableSupplierOnlyThrowExceptionInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
                   import java.util.Optional;
                   class Bar{
                   void foo(Optional<String> optional) {
                        optional.orElseThrow(() -> {
                            /*Throwable supplier doesn't return any exception*/throw new RuntimeException();/**/
                        });
                   }}""");
  }

  public void testSeveralThrow() {
    doTest("""
                   import java.util.Optional;
                   class Bar{
                   void foo(Optional<String> optional, String[] args) {
                      optional.orElseThrow(/*Throwable supplier doesn't return any exception*/() -> {
                          if (args.length == 1) {
                              throw  new RuntimeException("test");
                          } else {
                              throw new RuntimeException();
                          }
                      }/**/);
                   }}""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowableSupplierOnlyThrowExceptionInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}