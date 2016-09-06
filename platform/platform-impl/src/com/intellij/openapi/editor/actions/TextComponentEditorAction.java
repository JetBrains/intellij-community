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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public abstract class TextComponentEditorAction extends EditorAction {
  protected TextComponentEditorAction(@NotNull EditorActionHandler defaultHandler) {
    super(defaultHandler);
  }

  @Override
  @Nullable
  protected Editor getEditor(@NotNull final DataContext dataContext) {
    return getEditorFromContext(dataContext);
  }

  @Nullable
  public static Editor getEditorFromContext(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null) return editor;
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Object data = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (data instanceof JTextComponent) {
      return new TextComponentEditorImpl(project, (JTextComponent)data);
    }
    if (data instanceof JComponent) {
      final JTextField field = findActiveSpeedSearchTextField((JComponent)data);
      if (field != null) {
        return new TextComponentEditorImpl(project, field);
      }
    }
    return null;
  }

  private static JTextField findActiveSpeedSearchTextField(JComponent c) {
    final SpeedSearchSupply supply = SpeedSearchSupply.getSupply(c);
    if (supply instanceof SpeedSearchBase) {
      return ((SpeedSearchBase)supply).getSearchField();
    }
    if (c instanceof DataProvider) {
      final Object component = PlatformDataKeys.SPEED_SEARCH_COMPONENT.getData((DataProvider)c);
      if (component instanceof JTextField) {
        return (JTextField)component;
      }
    }
    return null;
  }
}