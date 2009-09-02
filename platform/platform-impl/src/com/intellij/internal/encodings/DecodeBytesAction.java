package com.intellij.internal.encodings;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class DecodeBytesAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    new EncodingViewer().show();
  }
}
