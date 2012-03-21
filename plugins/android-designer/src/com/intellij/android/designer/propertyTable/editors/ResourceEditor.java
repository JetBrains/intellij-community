/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.propertyTable.editors;

import com.android.resources.ResourceType;
import com.intellij.android.designer.model.PropertyParser;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.editors.ComboEditor;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class ResourceEditor extends PropertyEditor {
  private ResourceType[] myTypes;
  private ComponentWithBrowseButton myEditor;
  private RadComponent myRootComponent;

  public ResourceEditor(Set<AttributeFormat> formats, String[] values) {
    Set<ResourceType> types = EnumSet.noneOf(ResourceType.class);
    for (AttributeFormat format : formats) {
      switch (format) {
        case Boolean:
          types.add(ResourceType.BOOL);
          break;
        case Color:
          types.add(ResourceType.COLOR);
          types.add(ResourceType.DRAWABLE);
          break;
        case Dimension:
          types.add(ResourceType.DIMEN);
          break;
        case Integer:
          types.add(ResourceType.INTEGER);
          break;
        case String:
          types.add(ResourceType.STRING);
          break;
        case Reference:
          types.add(ResourceType.COLOR);
          types.add(ResourceType.DRAWABLE);
          types.add(ResourceType.STRING);
          types.add(ResourceType.ID);
          types.add(ResourceType.STYLE);
          break;
      }
    }
    myTypes = types.toArray(new ResourceType[types.size()]);

    if (formats.contains(AttributeFormat.Enum) || formats.contains(AttributeFormat.Boolean)) {
      ComboboxWithBrowseButton editor = new ComboboxWithBrowseButton();

      JComboBox comboBox = editor.getComboBox();
      DefaultComboBoxModel model;
      if (formats.contains(AttributeFormat.Boolean)) {
        model = new DefaultComboBoxModel(new String[]{StringsComboEditor.UNSET, "true", "false"});
      }
      else {
        model = new DefaultComboBoxModel(values);
        model.insertElementAt(StringsComboEditor.UNSET, 0);
      }
      comboBox.setModel(model);
      comboBox.setEditable(true);
      comboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          fireValueCommitted(false, true);
        }
      });
      ComboEditor.addEditorSupport(this, comboBox);

      myEditor = editor;
    }
    else {
      myEditor = new TextFieldWithBrowseButton();
    }

    myEditor.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertyParser parser = myRootComponent.getClientProperty(PropertyParser.KEY);
        ResourceDialog dialog = parser.createResourceDialog(myTypes);
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          getComboText().setText(dialog.getResourceName());
          fireValueCommitted(false, true);
        }
      }
    });
    myEditor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myEditor.getChildComponent().requestFocus();
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent(@NotNull RadComponent rootComponent, @Nullable RadComponent component, Object value) {
    myRootComponent = rootComponent;
    JTextField text = getComboText();
    text.setText((String)value);
    return myEditor;
  }

  @Override
  public Object getValue() throws Exception {
    String value = getComboText().getText();
    return StringsComboEditor.UNSET.equals(value) || StringUtil.isEmpty(value) ? null : value;
  }

  private JTextField getComboText() {
    JComponent component = myEditor.getChildComponent();
    if (component instanceof JTextField) {
      return (JTextField)component;
    }
    JComboBox combo = (JComboBox)component;
    return (JTextField)combo.getEditor().getEditorComponent();
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myEditor);
  }
}