// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BoundedWildcardInspectionTest extends LightInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
  
  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/bounded_wildcard";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package my;" +
      "public interface Processor<T> {" +
      "    boolean process(T t);" +
      "}",

      "package java.util.function;" +
      "public interface Function<T, R> {" +
      "    R apply(T t);\n" +
      "    default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) { return null;    }\n" +
      "    <V> Function<V, R> compose(Function<? super V, ? extends T> before) { return null;    }\n" +
      "}",

      "package java.util.function;\n" +
      "public interface Supplier<T> {\n" +
      "    T get();\n" +
      "}",

      "package my;\n" +
      "public interface Consumer<T> {\n" +
      "    void consume(T t);\n" +
      "}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    BoundedWildcardInspection inspection = new BoundedWildcardInspection();
    if (getTestName(false).contains("SwitchedOff")) {
      inspection.REPORT_INVARIANT_CLASSES = false;
    }
    return inspection;
  }

  public void testSimple() { doTest(); }
  public void testInvariantSwitchedOff() {
    doTest();
  }
}