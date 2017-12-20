/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import static com.intellij.openapi.util.SystemInfo.isMac;

final class CopyAction extends AbstractAction {
  private final Supplier<Transferable> supplier;

  CopyAction(@NotNull Supplier<Transferable> supplier) {
    super(isMac ? "Copy Preferences Path" : "Copy Settings Path");
    this.supplier = supplier;
  }

  @NotNull
  static <T> Transferable createTransferable(@NotNull Iterable<T> iterable) {
    StringBuilder sb = new StringBuilder(isMac ? "Preferences" : "File | Settings");
    for (T object : iterable) sb.append(" | ").append(object);
    return new TextTransferable(sb.toString());
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    Transferable transferable = supplier.get();
    if (transferable != null) {
      CopyPasteManager.getInstance().setContents(transferable);
    }
  }
}
