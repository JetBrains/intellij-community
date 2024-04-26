// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;

public final class JUnit5TestFrameworkSetupUtil {
  public static JavaCodeInsightTestFixture setupJUnit5Library(JavaCodeInsightTestFixture fixture) {
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface MethodSource {String[] value() default \"\";}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                      "public @interface EmptySource {}");
    fixture.addClass("""
                       package org.junit.jupiter.params;
                       @org.junit.platform.commons.annotation.Testable
                       public @interface ParameterizedTest {String name() default  "";}""");
    fixture.addClass("""
                       package org.junit.jupiter.params.provider;
                       public interface Arguments {static Arguments of(Object... arguments){}}
                       """);
    fixture.addClass("package org.junit.platform.commons.annotation;\n" +
                       "public @interface Testable {}");
    fixture.addClass("""
                       package org.junit.jupiter.api;
                       @org.junit.platform.commons.annotation.Testable
                       public @interface Test {}""");
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
    fixture.addClass("""
                       package org.junit.jupiter.params.provider;
                       public @interface NullSource {}
                       """);
    fixture.addClass("""
                       package org.junit.jupiter.params.provider;
                       @ArgumentsSource(ValueArgumentsProvider.class)
                       public @interface ValueSource {
                       String[] strings() default {};
                       int[] ints() default {};
                       long[] longs() default {};
                       double[] doubles() default {};
                       boolean[] booleans() default {};
                       }
                       """);
    fixture.addClass("""
                       package org.junit.jupiter.api.extension;
                       public @interface ExtendWith {
                         Class[] value();
                       }
                       """);
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface CsvSource {String[] value();}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface CsvFileSource {String[] value();}");
    fixture.addClass( "package org.junit.jupiter.params.provider;\n" +
                        "public @interface ArgumentsSource {}");
    fixture.addClass("""
                       package org.junit.jupiter.params.provider;
                       public @interface ArgumentsSources {
                        ArgumentsSource[] value();
                       }
                       """);
    fixture.addClass("""
                       package org.junit.jupiter.api;
                       public @interface TestInstance {
                       enum Lifecycle {PER_CLASS, PER_METHOD;}
                       Lifecycle value();}""");
    fixture.addClass("""
                       package org.junit.jupiter.api;
                       @org.junit.platform.commons.annotation.Testable
                       public @interface RepeatedTest {int value(); String name() default "";}""");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface AfterEach {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeAll {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public @interface BeforeEach {}");
    fixture.addClass("package org.junit.jupiter.api;\n" +
                        "public interface RepetitionInfo {}");
    fixture.addClass("""
                       package org.junit.jupiter.api.extension;
                       public @interface RegisterExtension {
                       }
                       """);
    fixture.addClass("""
                       package org.junit.jupiter.api.extension;
                       public interface Extension {
                       }
                       """);
    fixture.addClass("package org.junit.jupiter.api; public @interface Nested{}");
    fixture.addClass("""
                       package org.junit.jupiter.api.io;
                       @Target({ ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.PARAMETER })
                       public @interface TempDir {}
                       """);
    return fixture;
  }
}
