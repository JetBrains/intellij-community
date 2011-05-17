/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TestDirDiffAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new DialogWrapper(e.getData(PlatformDataKeys.PROJECT)) {
      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final JBTable table = new JBTable();
        table.setModel(new DefaultTableModel(new Object[][]{{"aaa", "bbb"}, {"ccc", "ddd"}}, new Object[]{"foo", "bar"}));
        panel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
        return panel;
      }
    }.show();
  }
}
