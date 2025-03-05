// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class InlineHandlers extends LanguageExtension<InlineHandler> {
  private static final InlineHandlers INSTANCE = new InlineHandlers();

  private InlineHandlers() {
    super("com.intellij.refactoring.inlineHandler");
  }

  public static List<InlineHandler> getInlineHandlers(Language language) {
    return INSTANCE.allForLanguage(language);
  }
}