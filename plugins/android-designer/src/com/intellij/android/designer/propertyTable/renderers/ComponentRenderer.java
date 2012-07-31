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
package com.intellij.android.designer.propertyTable.renderers;

import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public abstract class ComponentRenderer extends ColoredListCellRenderer implements PropertyRenderer {
  @Override
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    clear();
    PropertyTable.updateRenderer(this, selected);

    if (value == StringsComboEditor.UNSET) {
      append(StringsComboEditor.UNSET);
    }
    else if (value instanceof RadComponent) {
      renderComponent((RadComponent)value);
    }
  }

  private void renderComponent(RadComponent component) {
    TreeComponentDecorator decorator = component.getRoot().getClientProperty(TreeComponentDecorator.KEY);
    decorator.decorate(component, this, AttributeWrapper.DEFAULT, false);
    setIcon(component.getMetaModel().getIcon());
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 PropertyContext context,
                                 @Nullable Object object,
                                 boolean selected,
                                 boolean hasFocus) {
    clear();
    PropertyTable.updateRenderer(this, selected);

    String value = (String)object;
    RadComponent idComponent = container instanceof RadComponent ? getComponentById((RadComponent)container, value) : null;

    if (idComponent != null) {
      renderComponent(idComponent);
    }
    else if (!StringUtil.isEmpty(value)) {
      append("<not found>", SimpleTextAttributes.ERROR_ATTRIBUTES);
    }

    return this;
  }

  @Nullable
  public abstract RadComponent getComponentById(RadComponent component, String value);

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(this);
  }
}