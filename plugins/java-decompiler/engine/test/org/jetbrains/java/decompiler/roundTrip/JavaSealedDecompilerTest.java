// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip;

import org.jetbrains.java.decompiler.roundTrip.fixtures.java.JavaDecompilerRoundTripTestCase;
import org.junit.jupiter.api.Test;

public class JavaSealedDecompilerTest extends JavaDecompilerRoundTripTestCase {
  @Override
  protected String testCaseDir() {
    return "java/sealed";
  }

  @Test
  public void testRootWithClassInner() {
    doTest("sealed/RootWithClassInner");
  }

  @Test
  public void testRootWithInterfaceInner() {
    doTest("sealed/RootWithInterfaceInner");
  }

  @Test
  public void testRootWithClassOuter() {
    doTest("sealed/RootWithClassOuter", "src");
  }

  @Test
  public void testRootWithClassOuterUnresolvable() {
    doTest("sealed/RootWithClassOuter", "src");
  }

  @Test
  public void testRootWithInterfaceOuter() {
    doTest("sealed/RootWithInterfaceOuter", "src");
  }

  @Test
  public void testClassNonSealed() {
    doTest("sealed/ClassNonSealed", "src");
  }

  @Test
  public void testClassNonSealedExtendsImplements() {
    doTest("sealed/ClassNonSealedExtendsImplements", "src");
  }

  @Test
  public void testInterfaceNonSealed() {
    doTest("sealed/InterfaceNonSealed", "src");
  }

  @Test
  public void testRootWithModule() {
    doTest("sealed/foo/RootWithModule", "src");
  }

  @Test
  public void testRootWithInterfaceInnerAndOuter() {
    doTest("sealed/RootWithInterfaceInnerAndOuter", "src");
  }

  @Test
  public void testEnumWithOverride() {
    doTest("sealed/EnumWithOverride");
  }
}
