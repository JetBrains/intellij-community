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
