// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import org.jetbrains.annotations.NotNull;

public interface ExtractMethodDecorator<T> {
  String createMethodSignature(@NotNull ExtractMethodSettings<T> settings);
}