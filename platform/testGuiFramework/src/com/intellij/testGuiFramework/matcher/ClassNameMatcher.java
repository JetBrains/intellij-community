/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Matcher that checks the name of the actual implementation. This is useful when matching
 * against non-public IJ classes.
 */
public class ClassNameMatcher<T extends Component> extends GenericTypeMatcher<T> {
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
    return new ClassNameMatcher<T>(className, supportedType);
  }

  public static <T extends Component> ClassNameMatcher<T> forClass(String className, Class<T> supportedType, boolean requireShowing) {
    return new ClassNameMatcher<T>(className, supportedType, requireShowing);
  }
}
