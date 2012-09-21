// Copyright Robin Stevens
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


final class HgRepositorySettingsListRenderer extends JPanel implements ListCellRenderer{

  private final JCheckBox myCheckBox = new JCheckBox();
  private final ListCellRenderer myDelegate;
  private HgPushDialog.HgRepositorySettings myLastRenderedSettings;

  HgRepositorySettingsListRenderer( ListCellRenderer delegateRenderer ) {
    myDelegate = delegateRenderer;
    setLayout( new BorderLayout() );

    add(myCheckBox, BorderLayout.WEST);
    add((Component)myDelegate, BorderLayout.CENTER);

    setOpaque(false);
    myCheckBox.setOpaque(false);

    setFocusable(false);
    myCheckBox.setFocusable(false);
    myCheckBox.setMargin(new Insets(1, 0, 1, 2));

    myCheckBox.setBorderPaintedFlat(true);

    selectingCheckBoxShouldAdjustSelectableState();
  }

  private void selectingCheckBoxShouldAdjustSelectableState(){
    myCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myLastRenderedSettings == null) {
          return;
        }
        myLastRenderedSettings.setSelected(myCheckBox.isSelected());
      }
    });
  }

  @Override
  public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b1) {
    JLabel label = (JLabel)myDelegate.getListCellRendererComponent(jList, o, i, b, b1);

    setBackground(label.getBackground());
    setForeground(label.getForeground());

    HgPushDialog.HgRepositorySettings settings = (HgPushDialog.HgRepositorySettings)o;
    myLastRenderedSettings = settings;

    myCheckBox.setSelected(settings.isSelected());
    String labelText = settings.getRepository().getPresentableName();
    if (!(settings.isValid())){
      labelText += " (!)";
    }
    label.setText(labelText);
    label.setToolTipText(settings.getRepository().getPresentableUrl());

    myCheckBox.setEnabled(settings.isValid());
    label.setEnabled(settings.isValid());

    return this;
  }
}
