// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.CharSequenceReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.List;

public class TextTransferable implements Transferable {
  private static final Logger LOG = Logger.getInstance(TextTransferable.class);

  private final CharSequence myHtmlContent;
  private final CharSequence myPlainContent;

  private static final NotNullLazyValue<List<DataFlavor>> FLAVORS = NotNullLazyValue.createValue(() -> {
    List<DataFlavor> result = new ArrayList<>();
    result.add(DataFlavor.stringFlavor);
    //noinspection deprecation
    result.add(DataFlavor.plainTextFlavor);
    try {
      result.add(new DataFlavor("text/html;class=java.lang.String"));
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    return result;
  });

  // old constructor to preserve backward compatibility
  public TextTransferable(@Nullable String data) {
    this(StringUtil.notNullize(data), StringUtil.notNullize(data));
  }

  public TextTransferable(@NotNull CharSequence data) {
    this(data, data);
  }

  // old constructor to preserve backward compatibility
  public TextTransferable(@NotNull String htmlContent, @NotNull String plainContent) {
    myHtmlContent = StringUtil.notNullize(htmlContent);
    myPlainContent = StringUtil.notNullize(plainContent);
  }

  public TextTransferable(@NotNull CharSequence htmlContent, @NotNull CharSequence plainContent) {
    myHtmlContent = htmlContent;
    myPlainContent = plainContent;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return FLAVORS.getValue().toArray(new DataFlavor[0]);
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return FLAVORS.getValue().contains(flavor);
  }

  @Override
  public Object getTransferData(@NotNull DataFlavor flavor) throws UnsupportedFlavorException {
    if (flavor.getMimeType().startsWith("text/html;")) {  // NON-NLS
      return myHtmlContent.toString();
    }
    else if (flavor.equals(DataFlavor.plainTextFlavor)) {
      return new CharSequenceReader(myPlainContent == null ? "" : myPlainContent);
    }
    else if (flavor.equals(DataFlavor.stringFlavor)) {
      return myPlainContent.toString();
    }

    throw new UnsupportedFlavorException(flavor);
  }

  public static class ColoredStringBuilder implements ColoredTextContainer {
    private final StringBuilder builder = new StringBuilder();

    public void appendTo(StringBuilder @NotNull ... subBuilders) {
      for (StringBuilder subBuilder : subBuilders) {
        subBuilder.append(builder);
      }
      builder.setLength(0);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      builder.append(fragment);
    }

    public StringBuilder getBuilder() {
      return builder;
    }
  }
}
