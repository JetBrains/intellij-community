/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;

import javax.swing.*;

public class ListSpeedSearch extends SpeedSearchBase<JList> {
  private final Convertor<Object, String> myToStringConvertor;

  public ListSpeedSearch(JList list) {
    super(list);
    myToStringConvertor = null;
  }

  public ListSpeedSearch(final JList component, final Convertor<Object, String> convertor) {
    super(component);
    myToStringConvertor = convertor;
  }

  public ListSpeedSearch(final JList component, final Function<Object, String> convertor) {
    this(component, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        return convertor.fun(o);
      }
    });
  }

  protected void selectElement(Object element, String selectedText) {
    ScrollingUtil.selectItem(myComponent, element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    return getAllListElements(myComponent);
  }

  public static Object[] getAllListElements(final JList list) {
    ListModel model = list.getModel();
    if (model instanceof DefaultListModel){ // optimization
      return ((DefaultListModel)model).toArray();
    }
    else{
      Object[] elements = new Object[model.getSize()];
      for(int i = 0; i < elements.length; i++){
        elements[i] = model.getElementAt(i);
      }
      return elements;
    }
  }

  protected String getElementText(Object element) {
    if (myToStringConvertor != null) {
      return myToStringConvertor.convert(element);
    }
    return element == null ? null : element.toString();
  }
}