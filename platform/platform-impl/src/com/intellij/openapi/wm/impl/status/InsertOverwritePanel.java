package com.intellij.openapi.wm.impl.status;

import com.intellij.ui.UIBundle;
import com.intellij.openapi.editor.Editor;

import javax.swing.*;

/**
 * @author cdr
 */
public class InsertOverwritePanel extends TextPanel implements StatusBarPatch{
  public InsertOverwritePanel() {
    super(false, UIBundle.message("status.bar.column.status.text"), UIBundle.message("status.bar.insert.status.text"), UIBundle.message("status.bar.overwrite.status.text"));
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    boolean enabled = false;
    if (selected != null) {
      enabled = selected.getDocument().isWritable();

      String text = selected.isColumnMode()
                    ? UIBundle.message("status.bar.column.status.text")
                    : selected.isInsertMode()
                      ? UIBundle.message("status.bar.insert.status.text")
                      : UIBundle.message("status.bar.overwrite.status.text");
      setText(text);
    }
    setEnabled(enabled);
    return null;
  }

  public void clear() {
    setEnabled(false);
    setText("");
  }
}
