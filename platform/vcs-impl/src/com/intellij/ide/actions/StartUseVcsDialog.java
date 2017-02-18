/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class StartUseVcsDialog extends DialogWrapper {
  private final VcsDataWrapper myData;
  private VcsCombo myVcsCombo;
  private String mySelected;

  StartUseVcsDialog(final VcsDataWrapper data) {
    super(data.getProject(), true);
    myData = data;
    setTitle(VcsBundle.message("dialog.enable.version.control.integration.title"));

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myVcsCombo;
  }

  protected JComponent createCenterPanel() {
    final JLabel selectText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.select.vcs.label.text"));
    selectText.setUI(new MultiLineLabelUI());

    final JPanel mainPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0);

    mainPanel.add(selectText, gb);

    ++ gb.gridx;
    gb.anchor = GridBagConstraints.NORTHEAST;

    myVcsCombo = new VcsCombo(prepareComboData());
    mainPanel.add(myVcsCombo, gb);

    myVcsCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateVcs();
      }
    });
    validateVcs();

    final JLabel helpText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.hint.text"));
    helpText.setUI(new MultiLineLabelUI());
    helpText.setForeground(UIUtil.getInactiveTextColor());

    gb.anchor = GridBagConstraints.NORTHWEST;
    gb.gridx = 0;
    ++ gb.gridy;
    gb.gridwidth = 2;
    mainPanel.add(helpText, gb);

    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(mainPanel, new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                  new Insets(0,0,0,0), 0,0));
    return wrapper;
  }

  private void validateVcs() {
    final String selectedVcs = myVcsCombo.getSelectedItem();
    setOKActionEnabled(selectedVcs.length() > 0);
  }

  @Override
  protected String getHelpId() {
    return "reference.version.control.enable.version.control.integration";
  }

  @Override
  protected void doOKAction() {
    mySelected = myVcsCombo.getSelectedItem();
    super.doOKAction();    
  }

  private Object[] prepareComboData() {
    final Collection<String> displayNames = myData.getVcses().keySet();
    final List<String> keys = new ArrayList<>(displayNames.size() + 1);
    keys.add("");
    keys.addAll(displayNames);
    Collections.sort(keys);
    return ArrayUtil.toObjectArray(keys);
  }

  String getVcs() {
    return myData.getVcses().get(mySelected);
  }

  private static class VcsCombo extends JComboBox {
    private VcsCombo(final Object[] items) {
      super(items);
      setSelectedIndex(0);
      setEditable(false);
    }

    @Override
    public String getSelectedItem() {
      return (String) super.getSelectedItem();
    }
  }

}
