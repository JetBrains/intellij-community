// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TypeMayBeWeakenedInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // avoid PSI/document/model changes are not allowed during highlighting
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  public void testTypeMayBeWeakened() { doTest(); }
  public void testNumberAdderDemo() { doTest(); }
  public void testAutoClosableTest() { doTest(); }
  public void testLambda() { doTest(); }
  public void testTryWithResources() { doTest(); }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package weaken_type.sub;
public class NumberAdderImpl implements NumberAdder {
  public int doSomething() {
    return getNumberOne() + 1;
  }
  protected int getNumberOne() {
    return 1;
  }
}""",
      """
package weaken_type.sub;
public class NumberAdderExtension extends NumberAdderImpl {
  @Override
  public int getNumberOne() {
    return super.getNumberOne();
  }
}""",
      "package java.util.function;" +
      "@FunctionalInterface " +
      "public interface Function<T, R> {" +
      "    R apply(T t);" +
      "}",
      """
package java.util.function;
@FunctionalInterface
public interface Supplier<T> {
    T get();
}"""
    };
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/abstraction/weaken_type";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final TypeMayBeWeakenedInspection inspection = new TypeMayBeWeakenedInspection();
    inspection.doNotWeakenToJavaLangObject = false;
    inspection.doNotWeakenReturnType = false;
    inspection.onlyWeakentoInterface = false;
    return inspection;
  }
}