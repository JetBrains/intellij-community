/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;

public class TextTransferable implements Transferable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.TextTransferable");

  private final String myHtmlContent;
  private final String myPlainContent;

  private static DataFlavor html;
  private static DataFlavor[] flavors;

  private static DataFlavor[] getFlavours() {
    if (flavors == null) {
      try {
        html = new DataFlavor("text/html;class=java.lang.String");
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
        html = null;
      }

      flavors = (html == null) ? new DataFlavor[]{DataFlavor.stringFlavor, DataFlavor.plainTextFlavor} :
                new DataFlavor[]{DataFlavor.stringFlavor, DataFlavor.plainTextFlavor, html};
    }
    return flavors;
  }

  public TextTransferable(String data) {
    this(data, data);
  }

  public TextTransferable(String htmlContent, String plainContent) {
    myHtmlContent = htmlContent;
    myPlainContent = plainContent;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return getFlavours().clone();
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    for (DataFlavor f : getFlavours()) {
      if (flavor.equals(f)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (flavor.equals(html)) {
      return myHtmlContent;
    }
    else if (flavor.equals(DataFlavor.plainTextFlavor)) {
      return new StringReader(myPlainContent == null ? "" : myPlainContent);
    }
    else if (flavor.equals(DataFlavor.stringFlavor)) {
      return myPlainContent;
    }
    throw new UnsupportedFlavorException(flavor);
  }

  public static class ColoredStringBuilder implements ColoredTextContainer {
    private final StringBuilder builder = new StringBuilder();

    public void appendTo(@NotNull StringBuilder... subBuilders) {
      for (StringBuilder subBuilder : subBuilders) {
        subBuilder.append(builder);
      }
      builder.setLength(0);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      builder.append(fragment);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
      builder.append(fragment);
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
    }

    @Override
    public void setToolTipText(@Nullable String text) {
    }

    public StringBuilder getBuilder() {
      return builder;
    }
  }
}
