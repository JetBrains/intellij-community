// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.serialization.MissingSerialAnnotationInspection;
import org.jetbrains.annotations.Nullable;

public class AddSerialAnnotationFixTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.io;\n" +
      "import java.lang.annotation.*;\n" +
      "@Target({ElementType.METHOD, ElementType.FIELD})\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "public @interface Serial {}"
    };
  }

  public void testAdditionToField() {
    doTest("import java.io.*;\n" +
           "class Test implements Serializable {\n" +
           "  private static final long /*'serialVersionUID' can be annotated with '@Serial' annotation*//*_*/serialVersionUID/**/ = 7874493593505141603L;\n" +
           "}");
    checkQuickFix("Annotate field 'serialVersionUID' as @Serial", "import java.io.*;\n" +
                                    "class Test implements Serializable {\n" +
                                    "  @Serial\n" +
                                    "  private static final long serialVersionUID = 7874493593505141603L;\n" +
                                    "}");
  }

  public void testAdditionToMethod() {
    doTest("import java.io.*;\n" +
           "class Test implements Serializable {\n" +
           "  protected Object /*'readResolve()' can be annotated with '@Serial' annotation*//*_*/readResolve/**/() throws ObjectStreamException {\n" +
           "    return 1;\n" +
           "  }\n" +
           "}");
    checkQuickFix("Annotate method 'readResolve' as @Serial", "import java.io.*;\n" +
                                            "class Test implements Serializable {\n" +
                                            "  @Serial\n" +
                                            "  protected Object readResolve() throws ObjectStreamException {\n" +
                                            "    return 1;\n" +
                                            "  }\n" +
                                            "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new MissingSerialAnnotationInspection();
  }
}
