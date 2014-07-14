/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.10.2008
 */
public class GroovyDocReducePackageAction extends AnAction implements DumbAware {
  private final JList myPackagesList;
  private final DefaultListModel myDataModel;

  public GroovyDocReducePackageAction(final JList packagesList, final DefaultListModel dataModel) {
    super("Remove package from list", "Remove package from list", AllIcons.General.Remove);
    myPackagesList = packagesList;
    myDataModel = dataModel;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    myDataModel.remove(myPackagesList.getSelectedIndex());
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (myPackagesList.getSelectedIndex() == -1) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(true);
    }
    super.update(e);
  }
}
