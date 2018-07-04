/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public interface Accessor {
  Object read(@NotNull Object o);

  <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass);

  String getName();

  Class<?> getValueClass();

  Type getGenericType();

  boolean isFinal();
}
