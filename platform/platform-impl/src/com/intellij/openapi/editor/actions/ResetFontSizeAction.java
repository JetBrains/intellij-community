// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
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

  private interface Strategy {
    float getFontSize();
    void setFontSize(float fontSize);
    default void reset() {
      setFontSize(getFontSize());
    }
  }

  private static class SingleEditorStrategy implements Strategy {
    private final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();

    private final EditorEx myEditorEx;

    SingleEditorStrategy(EditorEx editorEx) {
      myEditorEx = editorEx;
    }

    @Override
    public float getFontSize() {
      if (ConsoleViewUtil.isConsoleViewEditor(myEditorEx)) {
        return globalScheme.getConsoleFontSize2D();
      }
      return globalScheme.getEditorFontSize2D();
    }

    @Override
    public void setFontSize(float fontSize) {
      myEditorEx.setFontSize(fontSize);
    }
  }

  private static class AllEditorsStrategy implements Strategy {
    private PropertiesComponent c = PropertiesComponent.getInstance();
    private final EditorEx myEditorEx;

    AllEditorsStrategy(EditorEx editorEx) {
      myEditorEx = editorEx;
    }

    @Override
    public float getFontSize() {
      if (ConsoleViewUtil.isConsoleViewEditor(myEditorEx)) {
        return c.getFloat(FONT_SIZE_TO_RESET_CONSOLE, -1);
      }
      return c.getFloat(FONT_SIZE_TO_RESET_EDITOR, -1);
    }

    @Override
    public void setFontSize(float fontSize) {
      EditorColorsManager.getInstance().getGlobalScheme().setEditorFontSize(fontSize);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorColorsManager.TOPIC).globalSchemeChange(null);
    }
  }

  private static Strategy getStrategy(EditorEx editor) {
    if (EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent()) {
      return new AllEditorsStrategy(editor);
    }
    return new SingleEditorStrategy(editor);
  }

  public ResetFontSizeAction() {
    super(new MyHandler());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (e.getPlace().equals(ActionPlaces.POPUP) && editor != null) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      EditorEx editorEx = (EditorEx)editor;
      //noinspection DialogTitleCapitalization
      e.getPresentation().setText(IdeBundle.message("action.reset.font.size", getStrategy(editorEx).getFontSize()));
    }
  }

  private static class MyHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      EditorEx editorEx = (EditorEx)editor;
      getStrategy(editorEx).reset();
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
