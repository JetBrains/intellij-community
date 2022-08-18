// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource;
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor;
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.stream.Collectors;

public class AllGradleVersionArgumentsProcessor extends DelegateArgumentsProcessor<AllGradleVersionsSource, GradleTestSource> {

  @Override
  public @NotNull ArgumentsProcessor<GradleTestSource> createArgumentsProcessor() {
    return new GradleTestArgumentsProcessor();
  }

  @Override
  public @NotNull GradleTestSource convertAnnotation(@NotNull AllGradleVersionsSource annotation) {
    return new GradleTestSource() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return GradleTestSource.class;
      }

      @Override
      public String[] values() {
        return annotation.value();
      }

      @Override
      public String value() {
        return Arrays.stream(VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS)
          .flatMap(it -> Arrays.stream(it))
          .collect(Collectors.joining(","));
      }

      @Override
      public char separator() {
        return ',';
      }

      @Override
      public char delimiter() {
        return ':';
      }
    };
  }

  @Override
  public void accept(@NotNull AllGradleVersionsSource annotation) {
    super.accept(annotation);
  }
}
