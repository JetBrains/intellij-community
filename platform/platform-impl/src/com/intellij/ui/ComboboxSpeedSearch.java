/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 11-Jul-2006
 * Time: 13:56:13
 */
package com.intellij.ui;

import javax.swing.*;

public class ComboboxSpeedSearch extends SpeedSearchBase<JComboBox> {
  public ComboboxSpeedSearch(JComboBox comboBox) {
    super(comboBox);
  }

  protected void selectElement(Object element, String selectedText) {
    myComponent.setSelectedItem(element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    ListModel model = myComponent.getModel();
    Object[] elements = new Object[model.getSize()];
    for(int i = 0; i < elements.length; i++){
      elements[i] = model.getElementAt(i);
    }
    return elements;
  }

  protected String getElementText(Object element) {
    return element.toString();
  }
}