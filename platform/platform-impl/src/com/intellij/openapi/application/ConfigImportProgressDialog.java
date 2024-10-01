// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class ConfigImportProgressDialog extends JDialog {
  private final ProgressIndicatorAdapter myIndicator = new ProgressIndicatorAdapter();
  private final JLabel myProgressTextLabel = new JLabel(" ");
  private final JProgressBar myProgressBar = new JProgressBar(0, 100);
  private boolean myCanceled;

  public ConfigImportProgressDialog() {
    super((Frame)null, IdeBundle.message("dialog.title.migrating.plugins"), true);
    JPanel panel = new JPanel();
    GridBag gridBag = new GridBag();
    panel.setLayout(new GridBagLayout());
    panel.add(new JLabel(IdeBundle.message("progress.text.migrating.plugins")), gridBag.nextLine().anchor(GridBagConstraints.WEST));
    panel.add(myProgressTextLabel, gridBag.nextLine().insetBottom(20));
    myProgressBar.setPreferredSize(new Dimension(JBUI.scale(500), myProgressBar.getPreferredSize().height));
    panel.add(myProgressBar, gridBag.nextLine().fillCell().insetBottom(20));
    JButton cancelButton = new JButton(IdeBundle.message("button.cancel.without.mnemonic"));
    panel.add(cancelButton, gridBag.nextLine());
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

  private final class ProgressIndicatorAdapter extends AbstractProgressIndicatorBase {
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

  @SuppressWarnings("HardCodedStringLiteral")
  public static void main(String[] args) {
    ConfigImportProgressDialog dialog = new ConfigImportProgressDialog();
    dialog.getIndicator().setText2("Downloading plugin 'Scala'");
    dialog.setVisible(true);
  }
}
