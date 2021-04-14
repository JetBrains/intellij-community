// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class IgnoredJUnitTestInspectionTest extends LightJavaInspectionTestCase {

  public void testIgnoredJUnitTest() {
    addEnvironmentClass("package org.junit;\n" +
                        "\n" +
                        "import java.lang.annotation.ElementType;\n" +
                        "import java.lang.annotation.Retention;\n" +
                        "import java.lang.annotation.RetentionPolicy;\n" +
                        "import java.lang.annotation.Target;\n" +
                        "\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target({ElementType.METHOD, ElementType.TYPE})\n" +
                        "public @interface Ignore {\n" +
                        "    String value() default \"\";\n" +
                        "}");
    addEnvironmentClass("package org.junit;\n" +
                        "\n" +
                        "import java.lang.annotation.ElementType;\n" +
                        "import java.lang.annotation.Retention;\n" +
                        "import java.lang.annotation.RetentionPolicy;\n" +
                        "import java.lang.annotation.Target;\n" +
                        "\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target(ElementType.METHOD)\n" +
                        "public @interface Test {}");
    doTest();
  }

  public void testDisabledJUnit5Test() {
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "\n" +
                        "import java.lang.annotation.ElementType;\n" +
                        "import java.lang.annotation.Retention;\n" +
                        "import java.lang.annotation.RetentionPolicy;\n" +
                        "import java.lang.annotation.Target;\n" +
                        "\n" +
                        "@Target({ElementType.TYPE, ElementType.METHOD})\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "public @interface Disabled {\n" +
                        "    String value() default \"\";\n" +
                        "}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "\n" +
                        "import java.lang.annotation.Documented;\n" +
                        "import java.lang.annotation.ElementType;\n" +
                        "import java.lang.annotation.Retention;\n" +
                        "import java.lang.annotation.RetentionPolicy;\n" +
                        "import java.lang.annotation.Target;\n" +
                        "\n" +
                        "@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "public @interface Test {}");
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new IgnoredJUnitTestInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16;
  }
}