// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class InlineHandlers extends LanguageExtension<InlineHandler> {
  private final static InlineHandlers INSTANCE = new InlineHandlers();

  private InlineHandlers() {
    super("com.intellij.refactoring.inlineHandler");
  }

  public static List<InlineHandler> getInlineHandlers(Language language) {
    return INSTANCE.allForLanguage(language);
  }
}