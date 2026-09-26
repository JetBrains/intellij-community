// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.jetbrains.annotations.ApiStatus.Internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use for marking places in code which are supposed to be updated after migration of the containing module to a newer JDK.
 */
@Internal
@Retention(RetentionPolicy.SOURCE)
public @interface ReviseWhenPortedToJDK {
  /**
   * A JDK version required for revising the marked code.
   */
  String value();

  /**
   * An additional description of what actually has to be done (i.e. a particular method to use).
   */
  String description() default "";
}