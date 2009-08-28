package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.project.DumbAware;

/**
 * @author yole
*/
public class RefreshFileChooserAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    VirtualFileManager.getInstance().refresh(false);
  }
}