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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.renderers.ComponentRenderer;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.editors.ComboEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class ComponentEditor extends ComboEditor {
  private final ComponentRenderer myRenderer;

  public ComponentEditor(ComponentRenderer renderer) {
    myRenderer = renderer;
    myCombo.setRenderer(renderer);
    myCombo.addActionListener(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myCombo.getSelectedItem() == StringsComboEditor.UNSET) {
          myCombo.setSelectedItem(null);
        }
      }
    });
  }

  @Override
  public Object getValue() throws Exception {
    if (myCombo.getSelectedItem() instanceof RadViewComponent) {
      RadViewComponent component = (RadViewComponent)myCombo.getSelectedItem();
      return component.getId();
    }
    return null;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 @Nullable PropertyContext context, Object value,
                                 @Nullable InplaceContext inplaceContext) {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement(StringsComboEditor.UNSET);

    myCombo.setModel(model);

    if (container instanceof RadComponent) {
      for (RadComponent childComponent : getComponents((RadComponent)container)) {
        model.addElement(childComponent);
      }
      myCombo.setSelectedItem(myRenderer.getComponentById((RadComponent)container, (String)value));
    }

    myCombo.setBorder(inplaceContext == null ? null : myComboBorder);
    return myCombo;
  }

  protected abstract List<RadComponent> getComponents(RadComponent component);
}