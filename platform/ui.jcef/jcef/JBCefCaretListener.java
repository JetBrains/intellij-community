// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.cef.misc.CefRange;

import java.awt.*;

interface JBCefCaretListener {
  void onImeCompositionRangeChanged(CefRange selectionRange, Rectangle[] characterBounds);
  void onTextSelectionChanged(String selectedText, CefRange selectionRange);
}
