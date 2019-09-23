// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.jediterm.terminal.DefaultTerminalCopyPasteHandler;
import com.jediterm.terminal.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public class IdeTerminalCopyPasteHandler extends DefaultTerminalCopyPasteHandler {

  private static final Logger LOG = Logger.getInstance(IdeTerminalCopyPasteHandler.class);

  @Override
  protected void setSystemClipboardContents(@NotNull String text) {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
    catch (IllegalStateException e) {
      String message = "Cannot set contents";
      if (UIUtil.isWindows) {
        LOG.debug(message, e);
      }
      else {
        LOG.warn(message, e);
      }
    }
  }
}
