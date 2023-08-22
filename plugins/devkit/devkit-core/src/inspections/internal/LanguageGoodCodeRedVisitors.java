// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.lang.LanguageExtension;

public final class LanguageGoodCodeRedVisitors extends LanguageExtension<GoodCodeRedVisitor> {
  public static final LanguageGoodCodeRedVisitors INSTANCE = new LanguageGoodCodeRedVisitors();

  private LanguageGoodCodeRedVisitors() {
    super("DevKit.lang.goodCodeRedVisitor");
  }

}