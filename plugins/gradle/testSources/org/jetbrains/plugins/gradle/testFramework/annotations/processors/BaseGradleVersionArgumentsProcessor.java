// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor;
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource;
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;

import java.lang.annotation.Annotation;

public class BaseGradleVersionArgumentsProcessor extends DelegateArgumentsProcessor<BaseGradleVersionSource, GradleTestSource> {

  @Override
  public @NotNull ArgumentsProcessor<GradleTestSource> createArgumentsProcessor() {
    return new GradleTestArgumentsProcessor();
  }

  @Override
  public @NotNull GradleTestSource convertAnnotation(@NotNull BaseGradleVersionSource annotation) {
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
        return VersionMatcherRule.BASE_GRADLE_VERSION;
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
  public void accept(@NotNull BaseGradleVersionSource annotation) {
    super.accept(annotation);
  }
}
