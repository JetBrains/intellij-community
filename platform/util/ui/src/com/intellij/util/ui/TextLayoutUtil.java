// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import java.util.Hashtable;

@ApiStatus.Internal
public final class TextLayoutUtil {

  private static boolean DISABLE_LAYOUT_IN_TEXT_COMPONENTS = false;

  public static void disableLayoutInTextComponents() {
    DISABLE_LAYOUT_IN_TEXT_COMPONENTS = true;
  }

  /**
   * Disables performing text layout for 'complex' text in the document, if configured globally.
   * Should be called before the document is used for anything, i.e., right after construction.
   */
  public static void disableTextLayoutIfNeeded(@NotNull Document document) {
    if (DISABLE_LAYOUT_IN_TEXT_COMPONENTS && document instanceof AbstractDocument ad) {
      ad.setDocumentProperties(new Hashtable<>(2) {
        @Override
        public synchronized Object get(Object key) {
          return "i18n".equals(key) ? Boolean.FALSE : super.get(key);
        }
      });
    }
  }
}
