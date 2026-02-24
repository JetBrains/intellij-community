// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip;

import org.jetbrains.java.decompiler.roundTrip.fixtures.java.JavaDecompilerRoundTripTestCase;
import org.junit.jupiter.api.Test;

public class JavaTypeAnnotationsDecompilerTest extends JavaDecompilerRoundTripTestCase {
  @Override
  protected String testCaseDir() {
    return "java/typeAnnotations";
  }

  @Test
  public void testArrayTypeAnnotations() {
    doTest("typeAnnotations/ArrayTypeAnnotations", "lib");
  }

  @Test
  public void testGenericTypeAnnotations() {
    doTest("typeAnnotations/GenericTypeAnnotations", "lib");
  }

  @Test
  public void testGenericArrayTypeAnnotations() {
    doTest("typeAnnotations/GenericArrayTypeAnnotations", "lib");
  }

  @Test
  public void testNestedTypeAnnotations() {
    doTest("typeAnnotations/NestedTypeAnnotations", "lib");
  }

  @Test
  public void testArrayNestedTypeAnnotations() {
    doTest("typeAnnotations/ArrayNestedTypeAnnotations", "lib");
  }

  @Test
  public void testGenericNestedTypeAnnotations() {
    doTest("typeAnnotations/GenericNestedTypeAnnotations", "lib");
  }

  @Test
  public void testGenericArrayNestedTypeAnnotations() {
    doTest("typeAnnotations/GenericArrayNestedTypeAnnotations", "lib");
  }

  @Test
  public void testClassSuperTypeAnnotations() {
    doTest("typeAnnotations/ClassSuperTypeAnnotations", "lib"); }

  @Test
  public void testInterfaceSuperTypeAnnotations() {
    doTest("typeAnnotations/InterfaceSuperTypeAnnotations", "lib");
  }

  @Test
  public void testMemberDeclarationTypeAnnotations() {
    doTest("typeAnnotations/MemberDeclarationTypeAnnotations", "lib");
  }

  @Test
  public void testNestedTypeAnnotationsParameters() {
    doTest("typeAnnotations/NestedTypeAnnotationsParameters", "lib");
  }
}
