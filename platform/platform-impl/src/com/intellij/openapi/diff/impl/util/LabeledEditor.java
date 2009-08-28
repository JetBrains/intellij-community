package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diff.DiffBundle;

import javax.swing.*;
import java.awt.*;

public class LabeledEditor extends JPanel {
  private final JLabel myLabel = new JLabel();

  public LabeledEditor() {
    super(new BorderLayout());
  }

  private String addReadOnly(String title, boolean readonly) {
    if (readonly) title += DiffBundle.message("diff.content.read.only.content.title.suffix");
    return title;
  }

  public void setComponent(JComponent component, String title) {
    removeAll();
    add(component, BorderLayout.CENTER);
    add(myLabel, BorderLayout.NORTH);
    setLabelTitle(title);
    revalidate();
  }

  private void setLabelTitle(String title) {
    myLabel.setText(title);
    myLabel.setToolTipText(title);
  }

  public void updateTitle(String title, boolean readonly) {
    setLabelTitle(addReadOnly(title, readonly));
  }
}
