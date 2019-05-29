// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

public interface Accessor {
  Object read(@NotNull Object o);

  Object readUnsafe(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  int readInt(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  long readLong(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  float readFloat(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  double readDouble(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  boolean readBoolean(@NotNull Object o) throws IllegalAccessException, InvocationTargetException;

  <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass);

  @NotNull
  String getName();

  @NotNull
  Class<?> getValueClass();

  @NotNull
  Type getGenericType();

  boolean isWritable();
}
