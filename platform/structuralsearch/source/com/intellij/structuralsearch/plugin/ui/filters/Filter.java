// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public interface Filter {

  int position();

  JComponent getRenderer();

  default FilterEditor getEditor() {
    return null;
  }
}
