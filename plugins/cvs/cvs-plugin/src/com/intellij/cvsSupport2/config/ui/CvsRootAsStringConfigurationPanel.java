// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditCvsConfigurationFieldByFieldDialog;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.cvsSupport2.ui.FormUtils;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsRootAsStringConfigurationPanel {

  private JTextField myCvsRoot;
  private JButton myEditFieldByFieldButton;
  private final Ref<Boolean> myIsUpdating;
  private final Collection<CvsRootChangeListener> myCvsRootListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private JPanel myPanel;

  public CvsRootAsStringConfigurationPanel(boolean readOnly, Ref<Boolean> isUpdating) {
    myIsUpdating = isUpdating;
    myCvsRoot.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        notifyListeners();
      }
    });

    myEditFieldByFieldButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final CvsRootConfiguration cvsRootConfiguration =
          CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
        cvsRootConfiguration.CVS_ROOT = FormUtils.getFieldValue(myCvsRoot, false);
        final EditCvsConfigurationFieldByFieldDialog dialog = new EditCvsConfigurationFieldByFieldDialog(myCvsRoot.getText());
        if (dialog.showAndGet()) {
          myCvsRoot.setText(dialog.getConfiguration());
        }
      }
    });
    if (readOnly) {
      myCvsRoot.setEditable(false);
      myEditFieldByFieldButton.setEnabled(false);
    }
  }

  protected void notifyListeners() {
    if (!myIsUpdating.isNull()) return;
    for (CvsRootChangeListener cvsRootChangeListener : myCvsRootListeners) {
      cvsRootChangeListener.onCvsRootChanged();
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void addCvsRootChangeListener(CvsRootChangeListener listener) {
    myCvsRootListeners.add(listener);
  }

  public void updateFrom(CvsRootConfiguration config) {
    myCvsRoot.setText(config.CVS_ROOT);
    myCvsRoot.selectAll();
  }

  public void saveTo(CvsRootConfiguration config) {
    config.CVS_ROOT = FormUtils.getFieldValue(myCvsRoot, true);
  }

  public String getCvsRoot() {
    return myCvsRoot.getText().trim();
  }

  public JComponent getPreferredFocusedComponent() {
    return myCvsRoot;
  }
}
