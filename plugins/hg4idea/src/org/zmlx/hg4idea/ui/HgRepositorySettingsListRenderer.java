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

  private final JCheckBox checkBox = new JCheckBox();
  private final ListCellRenderer delegate;
  private HgPushDialog2.HgRepositorySettings lastRenderedSettings;

  HgRepositorySettingsListRenderer( ListCellRenderer delegateRenderer ) {
    delegate = delegateRenderer;
    setLayout( new BorderLayout() );

    add(checkBox, BorderLayout.WEST);
    add((Component)delegate, BorderLayout.CENTER);

    setOpaque(false);
    checkBox.setOpaque(false);

    setFocusable(false);
    checkBox.setFocusable(false);
    checkBox.setMargin(new Insets(1,0,1,2));

    checkBox.setBorderPaintedFlat(true);

    selectingCheckBoxShouldAdjustSelectableState();
  }

  private void selectingCheckBoxShouldAdjustSelectableState(){
    checkBox.addActionListener( new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if ( lastRenderedSettings == null ){
          return;
        }
        lastRenderedSettings.setSelected(checkBox.isSelected());
      }
    });
  }

  @Override
  public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b1) {
    JLabel label = (JLabel)delegate.getListCellRendererComponent(jList, o, i, b, b1);

    setBackground(label.getBackground());
    setForeground(label.getForeground());

    HgPushDialog2.HgRepositorySettings settings = (HgPushDialog2.HgRepositorySettings)o;
    lastRenderedSettings = settings;

    checkBox.setSelected( settings.isSelected() );
    String labelText = settings.getRepository().getPresentableName();
    if (!(settings.isValid())){
      labelText += " (!)";
    }
    label.setText(labelText);
    label.setToolTipText(settings.getRepository().getPresentableUrl());

    checkBox.setEnabled(settings.isValid());
    label.setEnabled(settings.isValid());

    return this;
  }
}
