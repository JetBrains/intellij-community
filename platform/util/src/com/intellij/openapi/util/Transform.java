/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.util;

/**
 * @deprecated use com.intellij.util.Function
 */
public interface Transform <S, T> {
  T transform(S s);
}