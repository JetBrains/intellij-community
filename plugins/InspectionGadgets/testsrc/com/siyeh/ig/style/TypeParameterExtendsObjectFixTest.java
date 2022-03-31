// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class TypeParameterExtendsObjectFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TypeParameterExtendsObjectInspection());
  }

  public void testClassExtendingObject() {
    doTest(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E extends /**/Object> {\n" +
           "}",
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E> {\n" +
           "}");
  }

  public void testClassExtendingJavaLangObject() {
    doTest(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E extends java.lang./**/Object> {\n" +
           "}",
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E> {\n" +
           "}");
  }

  public void testAnnotationExtendingJavaLangObject() {
    doTest(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E extends @TypeParameterExtendsObject.A java.lang./**/Object> {\n" +
           "    @Target(ElementType.TYPE_USE)\n" +
           "    public @interface A{}\n" +
           "}",
           "import java.lang.annotation.ElementType;\n" +
           "import java.lang.annotation.Target;\n" +
           "\n" +
           "public class TypeParameterExtendsObject<E> {\n" +
           "    @Target(ElementType.TYPE_USE)\n" +
           "    public @interface A{}\n" +
           "}");
  }

  public void testDoNotFixClassExtendingOrgJetbrainsAnnotationsNotNull() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
                               "import java.lang.annotation.ElementType;\n" +
                               "import java.lang.annotation.Target;\n" +
                               "import java.util.List;\n" +
                               "\n" +
                               "public class TypeParameterExtendsObject<E extends @TypeParameterExtendsObject.A /**/org.jetbrains.annotations.NotNull> {\n" +
                               "    @Target(ElementType.TYPE_USE)\n" +
                               "    public @interface A{}\n" +
                               "}");
  }

  @Override
  protected String getRelativePath() {
    return "style/type.parameter.extends.object";
  }
}