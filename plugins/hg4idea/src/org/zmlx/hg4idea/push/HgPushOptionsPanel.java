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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class HgPushOptionsPanel extends VcsPushOptionsPanel {

  private final ComboBox myReferenceStrategyCombobox;

  public HgPushOptionsPanel() {
    setLayout(new BorderLayout());
    myReferenceStrategyCombobox = new ComboBox();
    HgVcsPushOptionValue[] values = HgVcsPushOptionValue.values();
    DefaultComboBoxModel comboModel = new DefaultComboBoxModel(values);
    myReferenceStrategyCombobox.setModel(comboModel);
    JLabel referenceStrategyLabel = new JLabel("Export Bookmarks: ");
    add(referenceStrategyLabel, BorderLayout.WEST);
    add(myReferenceStrategyCombobox, BorderLayout.CENTER);
  }

  @Override
  @NotNull
  public HgVcsPushOptionValue getValue() {
    return (HgVcsPushOptionValue)myReferenceStrategyCombobox.getSelectedItem();
  }

  @Override
  public void addValueChangeListener(ActionListener listener) {
    myReferenceStrategyCombobox.addActionListener(listener);
  }
}
