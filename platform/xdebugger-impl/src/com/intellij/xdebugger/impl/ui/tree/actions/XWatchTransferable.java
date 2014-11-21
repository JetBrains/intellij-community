/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.XExpression;

import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class XWatchTransferable extends StringSelection {
  public static final DataFlavor EXPRESSIONS_FLAVOR = new DataFlavor(List.class, "Debugger watches expressions");
  private final List<XExpression> myData;

  public XWatchTransferable(String data, List<XExpression> expressions) {
    super(data);
    myData = new ArrayList<XExpression>(expressions);
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
