// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 * This validator should check if name will clash with existing methods 
 */
public interface ExtractMethodValidator {
  @Nullable @NlsContexts.DialogMessage
  String check(String name);

  boolean isValidName(String name);
}