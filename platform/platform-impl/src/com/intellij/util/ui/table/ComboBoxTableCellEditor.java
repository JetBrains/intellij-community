// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.util.ArrayList;

/**
 * Supported value type: {@link ListWithSelection} and {@link Enum}.
 * Value type can implement {@link Iconable} to display icon.
 */
public class ComboBoxTableCellEditor extends DefaultCellEditor {
  public static final ComboBoxTableCellEditor INSTANCE = new ComboBoxTableCellEditor();

  private final JComboBox comboBox;

  public ComboBoxTableCellEditor() {
    //noinspection unchecked
    super(new JComboBox(new CollectionComboBoxModel(new ArrayList<>())));

    comboBox = (JComboBox)getComponent();

    // problem: pop-up opened - closed by esc - editing is not canceled, but must be
    comboBox.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        cancelCellEditing();
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        cancelCellEditing();
      }
    });

    comboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(value instanceof Iconable ? ((Iconable)value).getIcon(Iconable.ICON_FLAG_VISIBILITY) : null);
      @NlsSafe String text = value == null ? "" : value.toString();
      label.setText(text);
    }));
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (value instanceof ListWithSelection options) {
      comboBox.setModel(new CollectionComboBoxModel(options));

      if (options.getSelection() == null) {
        options.selectFirst();
      }
      comboBox.setSelectedItem(options.getSelection());
    }
    else {
      Enum enumValue = (Enum)value;
      Class enumClass = enumValue.getDeclaringClass();
      ComboBoxModel model = comboBox.getModel();
      if (!(model instanceof EnumComboBoxModel && model.getSize() > 0 && ((Enum)model.getElementAt(0)).getDeclaringClass() == enumClass)) {
        //noinspection unchecked
        comboBox.setModel(new EnumComboBoxModel(enumClass));
      }
      comboBox.setSelectedItem(value);
    }

    return comboBox;
  }
}