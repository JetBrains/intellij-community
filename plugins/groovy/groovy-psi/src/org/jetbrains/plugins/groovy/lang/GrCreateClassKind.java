// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.impl.quickfix.ClassKind;

public enum GrCreateClassKind implements ClassKind {
  CLASS     ("class"),
  INTERFACE ("interface"),
  TRAIT     ("trait"),
  ENUM      ("enum"),
  ANNOTATION("annotation");

  private final String myDescription;

  GrCreateClassKind(final String description) {
    myDescription = description;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }
}
