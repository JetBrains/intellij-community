/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RadioButtonEnumModel<E extends Enum<E>> {

  private final ButtonGroup myGroup;
  private final List<ButtonModel> myModels;
  private final List<E> myEnums;

  public static <E extends Enum<E>> RadioButtonEnumModel<E> bindEnum(Class<E> e, ButtonGroup group) {
    return new RadioButtonEnumModel<E>(e, group);
  }

  private RadioButtonEnumModel(Class<E> e, ButtonGroup group) {

    myGroup = group;
    myModels = ContainerUtil.map(Collections.list(myGroup.getElements()), new Function<AbstractButton, ButtonModel>() {
      @Override
      public ButtonModel fun(AbstractButton abstractButton) {
        return abstractButton.getModel();
      }
    });
    myEnums = Arrays.asList(e.getEnumConstants());
  }

  public E getSelected() {
    ButtonModel selection = myGroup.getSelection();
    int i = myModels.indexOf(selection);
    return myEnums.get(i);
  }

  public void setSelected(E e) {
    int i = myEnums.indexOf(e);
    myGroup.setSelected(myModels.get(i), true);
  }

  public void addActionListener(ActionListener listener) {
    for (AbstractButton button : Collections.list(myGroup.getElements())) {
      button.addActionListener(listener);
    }
  }
}
