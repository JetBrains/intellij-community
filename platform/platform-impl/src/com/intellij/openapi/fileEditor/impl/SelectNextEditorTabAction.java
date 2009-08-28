/*
 * @author max
 */
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

// The only purpose of this action is to serve as placeholder for assigning keyboard shortcuts.
// For actual tab switching code, see EditorComposite constructor.
public class SelectNextEditorTabAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
  }

  @Override
  public void update(AnActionEvent e) {
    // allow plugins to use the same keyboard shortcut
    e.getPresentation().setEnabled(false);
  }
}