// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.idea.extensions.IJIgnoreExtension;
import org.jetbrains.annotations.ApiStatus;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The main purpose of this annotation is to skip (ignore) flaky or red test on CI
 * independently of which JUnit the test is created with.
 * <br/>
 * Behaviour details:
 * For JUnit 3 and JUnit 4 test is ignored on CI only.
 * For JUnit 5 test is ignored both on CI and in local runs (because of simplicity of implementation via {@link ExtendWith})
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(IJIgnoreExtension.class)
@ApiStatus.Internal
public @interface IJIgnore {
  String issue();
}