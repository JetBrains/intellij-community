// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class ConfigImportProgressDialog extends JDialog {
  private final ProgressIndicatorAdapter myIndicator = new ProgressIndicatorAdapter();
  private final JLabel myProgressTextLabel = new JLabel(" ");
  private final JProgressBar myProgressBar = new JProgressBar(0, 100);
  private boolean myCanceled;

  public ConfigImportProgressDialog() {
    super((Frame)null, IdeBundle.message("dialog.title.migrating.plugins"), true);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    panel.add(new JLabel(IdeBundle.message("progress.text.migrating.plugins")));
    panel.add(myProgressTextLabel);
    myProgressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
    panel.add(myProgressBar);
    JButton cancelButton = new JButton(IdeBundle.message("button.cancel.without.mnemonic"));
    cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    panel.add(cancelButton);
    panel.setBorder(JBUI.Borders.empty(10, 20));
    cancelButton.addActionListener((e) -> {
      myCanceled = true;
    });
    setContentPane(panel);
    pack();

    setLocationRelativeTo(null);
  }

  public ProgressIndicatorAdapter getIndicator() {
    return myIndicator;
  }

  private class ProgressIndicatorAdapter extends AbstractProgressIndicatorBase {
    @Override
    public void setFraction(double fraction) {
      myProgressBar.setValue((int)(fraction * 100));
    }

    @Override
    public void setText2(String text) {
      myProgressTextLabel.setText(text);
    }

    @Override
    public boolean isCanceled() {
      return myCanceled;
    }

    @Override
    protected boolean isCancelable() {
      return true;
    }
  }

  public static void main(String[] args) {
    new ConfigImportProgressDialog().setVisible(true);
  }
}
