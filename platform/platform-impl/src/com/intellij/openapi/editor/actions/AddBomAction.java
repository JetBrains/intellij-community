// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Adds <a href="http://unicode.org/faq/utf_bom.html">file's BOM</a> to files with UTF-XXX encoding.
 */
final class AddBomAction extends AnAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = file != null
                      &&
                      file.getBOM() == null
                      &&
                      CharsetToolkit.getPossibleBom(file.getCharset()) !=
                      null; // support adding BOM to a single file only for the time being

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    e.getPresentation().setDescription(IdeBundle.messagePointer("add.byte.order.mark.to", enabled ? file.getName() : null));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) return;
    doAddBOM(file);
  }

  private static void doAddBOM(@NotNull VirtualFile virtualFile) {
    byte[] bom = virtualFile.getBOM();
    if (bom != null) return;
    Charset charset = virtualFile.getCharset();
    byte[] possibleBom = CharsetToolkit.getPossibleBom(charset);
    if (possibleBom == null) return;

    virtualFile.setBOM(possibleBom);
    try {
      byte[] bytes = virtualFile.contentsToByteArray();
      byte[] contentWithAddedBom = ArrayUtil.mergeArrays(possibleBom, bytes);
      WriteAction.runAndWait(() -> virtualFile.setBinaryContent(contentWithAddedBom));
    }
    catch (IOException ex) {
      Logger.getInstance(AddBomAction.class).warn("Unexpected exception occurred in file " + virtualFile, ex);
    }
  }
}
