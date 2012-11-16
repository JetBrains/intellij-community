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

import com.intellij.android.designer.propertyTable.editors.EventHandlerEditor;
import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class EventHandlerEditorRenderer extends ColoredListCellRenderer {
  @Override
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    clear();
    PropertyTable.updateRenderer(this, selected);

    if (value == StringsComboEditor.UNSET) {
      append(StringsComboEditor.UNSET);
    }
    else if (value instanceof EventHandlerEditor.PsiMethodWrapper) {
      PsiMethod method = ((EventHandlerEditor.PsiMethodWrapper)value).getMethod();
      setIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY));
      append(method.getName());

      PsiClass psiClass = method.getContainingClass();
      if (psiClass != null) {
        append(" (" + psiClass.getQualifiedName() + ")");
      }
    }
  }
}