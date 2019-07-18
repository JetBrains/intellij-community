// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use for marking places in code, which are supposed to be updated after migration of a containing module to newer JDK.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface ReviseWhenPortedToJDK {
  /**
   * A JDK version required for revising a marked code.
   */
  String value();
}