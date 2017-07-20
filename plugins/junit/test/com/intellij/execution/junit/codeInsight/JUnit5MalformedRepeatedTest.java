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

public class JUnit5MalformedRepeatedTest extends LightInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit5MalformedRepeatedTestInspection();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface RepeatedTest {int value(); String name() default \"\";}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface Test {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface AfterEach {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeAll {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeEach {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public interface RepetitionInfo {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public interface TestInfo {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public interface TestReporter {}");
    addEnvironmentClass("package org.junit.jupiter.api;\n" +
                        "public @interface ParameterizedTest {}");
  }

  public void testMalformed() { doTest(); }
  public void testPositiveRepetitions() { doTest(); }

  @Override
  protected String getBasePath() {
    return "/plugins/junit/testData/codeInsight/malformedRepeated";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
