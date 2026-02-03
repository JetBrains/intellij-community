// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.codeInsight.internal;

import com.intellij.lang.LanguageExtension;

public final class LanguageGoodCodeRedVisitors extends LanguageExtension<GoodCodeRedVisitor> {

  public static final LanguageGoodCodeRedVisitors INSTANCE = new LanguageGoodCodeRedVisitors();

  private LanguageGoodCodeRedVisitors() {
    super("com.intellij.dev.lang.goodCodeRedVisitor");
  }
}