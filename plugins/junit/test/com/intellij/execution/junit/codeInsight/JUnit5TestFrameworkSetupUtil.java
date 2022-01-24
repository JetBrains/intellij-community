// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;

public class JUnit5TestFrameworkSetupUtil {
  public static JavaCodeInsightTestFixture setupJUnit5Library(JavaCodeInsightTestFixture fixture) {
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface MethodSource {String[] value() default \"\";}");
    fixture.addClass( "package org.junit.jupiter.params;\n" +
                        "@org.junit.platform.commons.annotation.Testable\n" +
                        "public @interface ParameterizedTest {String name() default  \"\";}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public interface Arguments {static Arguments of(Object... arguments){}}\n");
    fixture.addClass("package org.junit.platform.commons.annotation;\n" +
                       "public @interface Testable {}");
    fixture.addClass( "package org.junit.jupiter.api;\n" +
                        "@org.junit.platform.commons.annotation.Testable\n" +
                        "public @interface Test {}");
    fixture.addClass( "package org.junit.jupiter.api;\n" +
                        "public interface TestInfo {}");
    fixture.addClass( "package org.junit.jupiter.api;\n" +
                        "public interface TestReporter {}");
    fixture.addClass("package org.junit.jupiter.params.provider;" +
                     "public enum NullEnum {;}");
    fixture.addClass( "package org.junit.jupiter.params.provider;" +
                        "public @interface EnumSource {" +
                        " Class<? extends Enum<?>> value() default NullEnum.class;" +
                        " String[] names() default {};" +
                        " Mode mode() default Mode.INCLUDE;" +
                        " enum Mode {" +
                        "  INCLUDE," +
                        "  EXCLUDE," +
                        "  MATCH_ALL," +
                        "  MATCH_ANY }" +
                        "}");
    fixture.addClass("package org.junit.jupiter.params.provider;\n" +
                     "public @interface NullSource {}\n");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "@ArgumentsSource(ValueArgumentsProvider.class)\n" +
                        "public @interface ValueSource {\n" +
                        "String[] strings() default {};\n" +
                        "int[] ints() default {};\n" +
                        "long[] longs() default {};\n" +
                        "double[] doubles() default {};\n" +
                        "boolean[] booleans() default {};\n" +
                        "}\n");
    fixture.addClass( "package org.junit.jupiter.api.extension;\n" +
                        "public @interface ExtendWith {\n" +
                        "  Class[] value();\n" +
                        "}\n");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface CsvSource {String[] value();}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface CsvFileSource {String[] value();}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface ArgumentsSource {}");
    fixture.addClass("package org.junit.jupiter.params.provider;\n" +
                       "public @interface ArgumentsSources {\n" +
                       " ArgumentsSource[] value();\n" +
                       "}\n");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                       "public @interface TestInstance {\n" +
                       "enum Lifecycle {PER_CLASS, PER_METHOD;}\n" +
                       "Lifecycle value();}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "@org.junit.platform.commons.annotation.Testable\n" +
                        "public @interface RepeatedTest {int value(); String name() default \"\";}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface AfterEach {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeAll {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeEach {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public interface RepetitionInfo {}");
    fixture.addClass("package org.junit.jupiter.api.extension;\n" +
                     "public @interface RegisterExtension {\n" +
                     "}\n");
    fixture.addClass("package org.junit.jupiter.api.extension;\n" +
                     "public interface Extension {\n" +
                     "}\n");
    return fixture;
  }
}