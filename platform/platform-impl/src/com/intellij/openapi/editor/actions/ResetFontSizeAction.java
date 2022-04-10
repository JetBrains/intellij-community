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
package com.intellij.openapi.editor.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 */
public class ResetFontSizeAction extends EditorAction {

  private static final String FONT_SIZE_TO_RESET_CONSOLE = "fontSizeToResetConsole";
  private static final String FONT_SIZE_TO_RESET_EDITOR = "fontSizeToResetEditor";
  public static final String PREVIOUS_COLOR_SCHEME = "previousColorScheme";

  private static float getResetFontSize(@NotNull Editor editor) {
    PropertiesComponent c = PropertiesComponent.getInstance();
    boolean isConsoleViewEditor = ConsoleViewUtil.isConsoleViewEditor(editor);
    float value;
    if (EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent()) { // all editors case
      if (isConsoleViewEditor) {
        value = c.getFloat(FONT_SIZE_TO_RESET_CONSOLE, -1);
      }
      else {
        value = c.getFloat(FONT_SIZE_TO_RESET_EDITOR, -1);
      }
    }
    else { // single editor
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (isConsoleViewEditor) {
        value = globalScheme.getConsoleFontSize2D();
      }
      else {
        value = globalScheme.getEditorFontSize2D();
      }
    }
    return value;
  }

  public ResetFontSizeAction() {
    super(new MyHandler());
  }
  
  private static class MyHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      float fontSize = getResetFontSize(editor);
      EditorEx editorEx = (EditorEx)editor;
      editorEx.setFontSize(fontSize);
    }
  }

  private static class Listener implements EditorColorsListener, ApplicationInitializedListener {
    @Override
    public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
      impl(false);
    }

    @Override
    public void componentsInitialized() {
      impl(true);
    }

    private void impl(boolean force) {
      PropertiesComponent c = PropertiesComponent.getInstance();
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (force || !c.getValue(PREVIOUS_COLOR_SCHEME, "").equals(globalScheme.getName())) {
        c.setValue(PREVIOUS_COLOR_SCHEME, globalScheme.getName());
        c.setValue(FONT_SIZE_TO_RESET_CONSOLE, globalScheme.getConsoleFontSize2D(), -1);
        c.setValue(FONT_SIZE_TO_RESET_EDITOR, globalScheme.getEditorFontSize2D(), -1);
      }
    }
  }
}
