// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class RadioButtonEnumModel<E extends Enum<E>> {

  private final ButtonGroup myGroup;
  private final List<ButtonModel> myModels;
  private final List<E> myEnums;

  public static <E extends Enum<E>> RadioButtonEnumModel<E> bindEnum(Class<E> e, ButtonGroup group) {
    return new RadioButtonEnumModel<>(e, group);
  }

  private RadioButtonEnumModel(Class<E> e, ButtonGroup group) {

    myGroup = group;
    myModels = ContainerUtil.map(Collections.list(myGroup.getElements()), AbstractButton::getModel);
    myEnums = Arrays.asList(e.getEnumConstants());
  }

  public E getSelected() {
    ButtonModel selection = myGroup.getSelection();
    int i = myModels.indexOf(selection);
    return myEnums.get(i);
  }

  public void setSelected(E e) {
    int i = myEnums.indexOf(e);
    setSelected(i);
  }

  public void addActionListener(ActionListener listener) {
    for (AbstractButton button : Collections.list(myGroup.getElements())) {
      button.addActionListener(listener);
    }
  }

  public void setSelected(int index) {
    myGroup.setSelected(myModels.get(index), true);
  }

  public AbstractButton getButton(E e) {
    int i = myEnums.indexOf(e);
    return Collections.list(myGroup.getElements()).get(i);
  }
}
