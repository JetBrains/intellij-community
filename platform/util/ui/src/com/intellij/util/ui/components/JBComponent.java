// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.components;

import com.intellij.util.ui.JBFont;

import javax.swing.border.Border;

/**
 * @author Konstantin Bulenkov
 */
public interface JBComponent<T extends JBComponent> {
  T withBorder(Border border);

  T withFont(JBFont font);

  T andTransparent();

  T andOpaque();
}
