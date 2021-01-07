// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Adds <a href="http://unicode.org/faq/utf_bom.html">file's BOM</a> to files with UTF-XXX encoding.
 */
public class AddBomAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AddBomAction.class);

  public AddBomAction() {
    super(IdeBundle.messagePointer("add.BOM"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = file != null
                      && file.getBOM() == null
                      && CharsetToolkit.getPossibleBom(file.getCharset()) != null
      ; // support adding BOM to a single file only for the time being
    
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
    NewVirtualFile file = (NewVirtualFile)virtualFile;
    try {
      byte[] bytes = file.contentsToByteArray();
      byte[] contentWithAddedBom = ArrayUtil.mergeArrays(possibleBom, bytes);
      WriteAction.runAndWait(() -> file.setBinaryContent(contentWithAddedBom));
    }
    catch (IOException ex) {
      LOG.warn("Unexpected exception occurred in file " + file, ex);
    }
  }
}
