// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.ui.AbstractListCellRenderer;
import com.intellij.icons.AllIcons;

import javax.swing.*;

/**
 * author: lesya
 */

public class CvsListCellRenderer extends AbstractListCellRenderer {

  @Override
  protected Icon getPresentableIcon(Object value) {
    if (value == null) return null;
    return AllIcons.Nodes.Cvs_global;
  }

  @Override
  protected String getPresentableString(Object value) {
    if (value == null)
      return "";
    else
      return value.toString();
  }

}
