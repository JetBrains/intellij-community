package com.siyeh.ig.abstraction;

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.extensions.Extensions;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TypeMayBeWeakenedInspectionTest extends LightInspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // avoid PSI/document/model changes are not allowed during highlighting
    Extensions.getExtensions(ToolExtensionPoints.DEAD_CODE_TOOL, null);
  }

  public void testTypeMayBeWeakened() { doTest(); }
  public void testNumberAdderDemo() { doTest(); }
  public void testAutoClosableTest() { doTest(); }
  public void testLambda() { doTest(); }
  public void testTryWithResources() { doTest(); }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package weaken_type.sub;\n" +
      "public class NumberAdderImpl implements NumberAdder {\n" +
      "  public int doSomething() {\n" +
      "    return getNumberOne() + 1;\n" +
      "  }\n" +
      "  protected int getNumberOne() {\n" +
      "    return 1;\n" +
      "  }\n" +
      "}",
      "package weaken_type.sub;\n" +
      "public class NumberAdderExtension extends NumberAdderImpl {\n" +
      "  @Override\n" +
      "  public int getNumberOne() {\n" +
      "    return super.getNumberOne();\n" +
      "  }\n" +
      "}",
      "package java.util.function;" +
      "@FunctionalInterface " +
      "public interface Function<T, R> {" +
      "    R apply(T t);" +
      "}",
      "package java.util.function;\n" +
      "@FunctionalInterface\n" +
      "public interface Supplier<T> {\n" +
      "    T get();\n" +
      "}"
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