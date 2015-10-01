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

import com.intellij.util.PairConvertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/14/12
 * Time: 1:56 PM
 */
public class MultipleTraitsListSpeedSearch extends MultipleTraitsSpeedSearch<JList> {
  public MultipleTraitsListSpeedSearch(JList component, @NotNull List<PairConvertor<Object, String, Boolean>> convertors) {
    super(component, convertors);
  }

  @Override
  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  @Override
  protected Object[] getAllElements() {
    return ListSpeedSearch.getAllListElements(myComponent);
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    ScrollingUtil.selectItem(myComponent, element);
  }
}
