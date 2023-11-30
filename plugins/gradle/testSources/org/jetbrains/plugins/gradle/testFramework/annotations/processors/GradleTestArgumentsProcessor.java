// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors;

import com.intellij.util.ArrayUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor;
import org.jetbrains.plugins.gradle.testFramework.annotations.CsvCrossProductSource;
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;

public class GradleTestArgumentsProcessor extends DelegateArgumentsProcessor<GradleTestSource, CsvCrossProductSource> {

  @Override
  public @NotNull ArgumentsProcessor<CsvCrossProductSource> createArgumentsProcessor() {
    return new CsvCrossProductArgumentsProcessor();
  }

  @Override
  public @NotNull CsvCrossProductSource convertAnnotation(@NotNull GradleTestSource annotation) {
    return new CsvCrossProductSource() {

      @Override
      public Class<? extends Annotation> annotationType() {
        return CsvCrossProductSource.class;
      }

      @Override
      public String[] value() {
        return ArrayUtil.prepend(annotation.value(), annotation.values());
      }

      @Override
      public char separator() {
        return annotation.separator();
      }

      @Override
      public char delimiter() {
        return annotation.delimiter();
      }
    };
  }

  @Override
  public @NotNull Arguments convertArguments(@NotNull Arguments arguments, @NotNull ExtensionContext context) {
    Assertions.assertNull(getTargetVersionsAnnotation(context), """
        @TargetVersions filter annotation isn't supported for Gradle JUnit 5 tests.
        Please, use assumptions on Gradle version instead.
        See, also: org.jetbrains.plugins.gradle.testFramework.util.GradleVersionAssumptionUtil
      """);

    var collection = new ArrayList<>();
    Collections.addAll(collection, arguments.get());
    collection.set(0, GradleVersion.version(collection.get(0).toString()));
    return Arguments.of(collection.toArray());
  }

  private static TargetVersions getTargetVersionsAnnotation(@NotNull ExtensionContext context) {
    return context.getTestMethod()
      .map(it -> it.getAnnotation(TargetVersions.class))
      .orElse(null);
  }

  @Override
  public void accept(@NotNull GradleTestSource annotation) {
    super.accept(annotation);
  }
}
