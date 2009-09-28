/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.lang.reflect.Field;

public class MultipleCheckboxOptionsPanel extends JPanel {

  private final BaseInspection owner;

  public MultipleCheckboxOptionsPanel(BaseInspection owner) {
    super(new GridBagLayout());
    this.owner = owner;
  }

  public void addCheckbox(String label,
                          @NonNls String property) {
    final boolean selected = getPropertyValue(owner, property);
    final JCheckBox checkBox = new JCheckBox(label, selected);
    final ButtonModel model = checkBox.getModel();
    final CheckboxChangeListener changeListener
        = new CheckboxChangeListener(owner, property, model);
    model.addChangeListener(changeListener);
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.FIRST_LINE_START;
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    final Component[] components = getComponents();
    removeAll();
    for (Component component : components) {
      add(component, constraints);
      constraints.gridy++;
    }
    constraints.weighty = 1.0;
    add(checkBox, constraints);
  }

  private static boolean getPropertyValue(BaseInspection owner,
                                          String property) {
    try {
      final Class<? extends BaseInspection> aClass = owner.getClass();
      final Field field = aClass.getField(property);
      return field.getBoolean(owner);
    } catch (IllegalAccessException ignored) {
      return false;
    } catch (NoSuchFieldException ignored) {
      return false;
    }
  }

  private static class CheckboxChangeListener implements ChangeListener {
    private final BaseInspection owner;
    private final String property;
    private final ButtonModel model;

    CheckboxChangeListener(BaseInspection owner, String property,
                           ButtonModel model) {
      this.owner = owner;
      this.property = property;
      this.model = model;
    }

    public void stateChanged(ChangeEvent e) {
      setPropertyValue(owner, property, model.isSelected());
    }

    private static void setPropertyValue(BaseInspection owner,
                                         String property,
                                         boolean selected) {
      try {
        final Class<? extends BaseInspection> aClass = owner.getClass();
        final Field field = aClass.getField(property);
        field.setBoolean(owner, selected);
      } catch (IllegalAccessException ignored) {
        // nothing
      } catch (NoSuchFieldException ignored) {
        // nothing
      }
    }
  }
}