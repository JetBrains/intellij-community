/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
* @author Konstantin Bulenkov
*/
class JBListTableFocusTraversalPolicy extends FocusTraversalPolicy {
  private final JBTableRowEditor myEditor;

  public JBListTableFocusTraversalPolicy(JBTableRowEditor editor) {
    myEditor = editor;
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    final List<Component> focusableComponents = Arrays.<Component>asList(myEditor.getFocusableComponents());
    int i = focusableComponents.indexOf(aComponent);
    if (i != -1) {
      i++;
      if (i >= focusableComponents.size()) {
        i = 0;
      }
      return focusableComponents.get(i);
    }
    return null;
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    final List<Component> focusableComponents = Arrays.<Component>asList(myEditor.getFocusableComponents());
    int i = focusableComponents.indexOf(aComponent);
    if (i != -1) {
      i--;
      if (i == -1) {
        i = focusableComponents.size() - 1;
      }
      return focusableComponents.get(i);
    }
    return null;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    final List<Component> focusableComponents = Arrays.<Component>asList(myEditor.getFocusableComponents());
    return focusableComponents.isEmpty() ? null : focusableComponents.get(0);
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    final List<Component> focusableComponents = Arrays.<Component>asList(myEditor.getFocusableComponents());
    return focusableComponents.isEmpty() ? null : focusableComponents.get(focusableComponents.size() - 1);
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    return myEditor.getPreferredFocusedComponent();
  }
}
