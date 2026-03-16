// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.ApiStatus;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class XWatchTransferable extends StringSelection {
  public static final DataFlavor EXPRESSIONS_FLAVOR = new DataFlavor(List.class, "Debugger watches expressions");
  private final List<XExpression> myData;

  public XWatchTransferable(String data, List<XExpression> expressions) {
    super(data);
    myData = new ArrayList<>(expressions);
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return ArrayUtil.mergeArrays(super.getTransferDataFlavors(), new DataFlavor[]{EXPRESSIONS_FLAVOR});
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    if (EXPRESSIONS_FLAVOR.equals(flavor)) return true;
    return super.isDataFlavorSupported(flavor);
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (EXPRESSIONS_FLAVOR.equals(flavor)) {
      return myData;
    }
    return super.getTransferData(flavor);
  }
}
