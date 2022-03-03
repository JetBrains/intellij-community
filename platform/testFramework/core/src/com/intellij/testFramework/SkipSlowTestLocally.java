// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.idea.extensions.SkipSlowExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark dog slow {@link com.intellij.testFramework.UsefulTestCase} implementations for skip in local test pass
 * if "skip.slow.tests.locally" property is defined.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SkipSlowExecutionCondition.class)
public @interface SkipSlowTestLocally { }
