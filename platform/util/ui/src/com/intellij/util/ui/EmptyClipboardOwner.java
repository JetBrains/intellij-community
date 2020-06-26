// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
*/
public final class EmptyClipboardOwner implements ClipboardOwner {
  public static EmptyClipboardOwner INSTANCE = new EmptyClipboardOwner();

  private EmptyClipboardOwner() {
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
  }
}