// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Matcher that checks the name of the actual implementation. This is useful when matching
 * against non-public IJ classes.
 */
public final class ClassNameMatcher<T extends Component> extends GenericTypeMatcher<T> {
  private final String myClassName;

  private ClassNameMatcher(String className, Class<T> supportedType) {
    super(supportedType);
    myClassName = className;
  }

  private ClassNameMatcher(String className, Class<T> supportedType, boolean requireShowing) {
    super(supportedType, requireShowing);
    myClassName = className;
  }

  @Override
  protected boolean isMatching(@NotNull T component) {
    return myClassName.equals(component.getClass().getName());
  }

  public static <T extends Component> ClassNameMatcher<T> forClass(String className, Class<T> supportedType) {
    return new ClassNameMatcher<>(className, supportedType);
  }

  public static <T extends Component> ClassNameMatcher<T> forClass(String className, Class<T> supportedType, boolean requireShowing) {
    return new ClassNameMatcher<>(className, supportedType, requireShowing);
  }
}
