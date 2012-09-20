package org.zmlx.hg4idea.ui;

import javax.swing.*;
import java.awt.*;


final class HgRepositorySettingsListRenderer extends JPanel implements ListCellRenderer{

  private final JCheckBox checkBox = new JCheckBox();
  private final ListCellRenderer delegate;

  HgRepositorySettingsListRenderer( ListCellRenderer delegateRenderer ) {
    delegate = delegateRenderer;
    setLayout(new FlowLayout( FlowLayout.LEFT));
  }

  @Override
  public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b1) {
    JLabel label = (JLabel)delegate.getListCellRendererComponent(jList, o, i, b, b1);

    HgPushDialog2.HgRepositorySettings settings = (HgPushDialog2.HgRepositorySettings)o;
    checkBox.setSelected( settings.isSelected() );
    label.setText( settings.getRepository().getPresentableName());
    label.setToolTipText(settings.getRepository().getPresentableUrl());

    checkBox.setEnabled(settings.isValid());
    label.setEnabled(settings.isValid());

    removeAll();
    add(checkBox);
    add(label);
    return this;
  }
}
