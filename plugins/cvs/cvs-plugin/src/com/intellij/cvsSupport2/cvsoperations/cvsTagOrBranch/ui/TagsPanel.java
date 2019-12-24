// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ClickListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */

public class TagsPanel extends JPanel implements TableCellRenderer{

  private static final Logger LOG = Logger.getInstance(TagsPanel.class);

  private final JLabel myTextLabel = new JLabel();

  private final JLabel myMoreLabel = new JLabel(MORE_LABEL_TEXT){
    private final Cursor myCursor = new Cursor(Cursor.HAND_CURSOR);
    @Override
    public Cursor getCursor() {
      return myCursor;
    }
  };

  private Collection<String> myTags;
  @NonNls private static final String MORE_LABEL_TEXT = "<html><b>(...)</b></html>";
  private final String myPopupTitle;

  public TagsPanel(final String popupTitle) {
    super(new BorderLayout());
    add(myTextLabel, BorderLayout.CENTER);
    add(myMoreLabel, BorderLayout.EAST);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        showTags();
        return true;
      }
    }.installOn(myMoreLabel);

    myPopupTitle = popupTitle;
  }

  private void showTags() {
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(new ArrayList<>(myTags)).
      setTitle(myPopupTitle).
      createPopup().
      showUnderneathOf(myMoreLabel);
  }

  public void setTags(Collection<String> tags) {
    myTags = tags;
    myMoreLabel.setVisible(myTags.size() > 1);
    if (myTags.size() > 0)
      myTextLabel.setText(myTags.iterator().next());
    revalidate();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setSelected(isSelected, table);
    if (!(value instanceof Collection)) {
      LOG.info("getTableCellRendererComponent: " + value == null ? null : value.toString());
      return this;
    }
    final Collection<String> tags = (Collection<String>) value;
    setTags(tags);
    return this;
  }

  public void setSelected(boolean isSelected, JTable table){
    if (isSelected) {
      setBackground(table.getSelectionBackground());
      setForeground(table.getSelectionForeground());
    } else {
      setBackground(table.getBackground());
      setForeground(table.getForeground());
    }

    updateLabel(myTextLabel, isSelected, table);
    updateLabel(myMoreLabel, isSelected, table);
  }

  private static void updateLabel(JLabel label, boolean isSelected, JTable table) {
    if (isSelected) {
      label.setBackground(table.getSelectionBackground());
      label.setForeground(table.getSelectionForeground());
    } else {
      label.setBackground(table.getBackground());
      label.setForeground(table.getForeground());
    }
  }
}
