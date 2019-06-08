// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GroovyDocReducePackageAction extends AnAction implements DumbAware {
  private final JList myPackagesList;
  private final DefaultListModel myDataModel;

  public GroovyDocReducePackageAction(final JList packagesList, final DefaultListModel dataModel) {
    super("Remove package from list", "Remove package from list", AllIcons.General.Remove);
    myPackagesList = packagesList;
    myDataModel = dataModel;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    myDataModel.remove(myPackagesList.getSelectedIndex());
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (myPackagesList.getSelectedIndex() == -1) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(true);
    }
  }
}
