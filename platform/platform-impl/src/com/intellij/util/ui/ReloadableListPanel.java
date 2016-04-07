/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ReloadableListPanel<T> extends ReloadablePanel<T> {
  protected JList myList;
  private JPanel myMainPanel;
  private JPanel myActionPanel;

  @SuppressWarnings("unchecked")
  public T getSelectedValue() {
    return (T)myList.getSelectedValue();
  }

  protected void createList() {
    myList = new JBList();
    new ListSpeedSearch(myList, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        return ((String)((Pair)o).getFirst());
      }
    });
  }

  @NotNull
  @Override
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myActionPanel = getActionPanel();
    createList();
  }
}
