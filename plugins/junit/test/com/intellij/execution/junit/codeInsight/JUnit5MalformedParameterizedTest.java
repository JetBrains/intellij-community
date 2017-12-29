/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnit5MalformedParameterizedTest extends LightInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit5MalformedParameterizedInspection();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addEnvironmentClass("package org.junit.platform.commons.annotation;\n" +
                        "public @interface Testable {}");
    addEnvironmentClass("package org.junit.jupiter.params;\n" +
                        "@org.junit.platform.commons.annotation.Testable\n" +
                        "public @interface ParameterizedTest {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "@org.junit.platform.commons.annotation.Testable\n" +
                        "public @interface Test {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public interface TestInfo {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public interface TestReporter {}");
    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "public @interface MethodSource {String[] value();}");
    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "public @interface EnumSource { Class<? extends Enum<?>> value();}");
    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "@ArgumentsSource(ValueArgumentsProvider.class)\n" +
                        "public @interface ValueSource {\n" +
                        "String[] strings() default {};\n" +
                        "int[] ints() default {};\n" +
                        "long[] longs() default {};\n" +
                        "double[] doubles() default {};\n" +
                        "}\n");
    addEnvironmentClass("package org.junit.jupiter.api.extension;\n" +
                        "public @interface ExtendWith {\n" +
                        "  Class[] value();\n" +
                        "}\n");
    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "public @interface CsvSource {String[] value();}");
    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "public interface Arguments {static Arguments of(Object... arguments){}}\n");

    addEnvironmentClass("package org.junit.jupiter.params.provider;\n" +
                        "public @interface ArgumentsSource {}");

    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface TestInstance {\n" +
                        "enum Lifecycle {PER_CLASS, PER_METHOD;}\n" +
                        "Lifecycle value();}");
  }

  public void testMalformedSources() { doTest(); }
  public void testMethodSource() { doTest(); }
  public void testMalformedSourcesImplicitConversion() { doTest(); }
  public void testMalformedSourcesImplicitParameters() { doTest(); }
  public void testMalformedSourcesTestInstancePerClass() { doTest(); }

  @Override
  protected String getBasePath() {
    return "/plugins/junit/testData/codeInsight/malformedParameterized";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
