// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResetFontSizeAction extends EditorAction {
  private static final String FONT_SIZE_TO_RESET_CONSOLE = "fontSizeToResetConsole";
  private static final String FONT_SIZE_TO_RESET_EDITOR = "fontSizeToResetEditor";
  public static final String PREVIOUS_COLOR_SCHEME = "previousColorScheme";

  @ApiStatus.Internal
  public interface Strategy {
    float getFontSize();
    void setFontSize(float fontSize);

    @NlsActions.ActionText String getText(float fontSize);
    default void reset() {
      setFontSize(getFontSize());
    }
  }

  private static final class SingleEditorStrategy implements Strategy {
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

    @Override
    public String getText(float fontSize) {
      return IdeBundle.message("action.reset.font.size", fontSize);
    }
  }

  private static final class AllEditorsStrategy implements Strategy {
    private final EditorEx myEditorEx;

    AllEditorsStrategy(EditorEx editorEx) {
      myEditorEx = editorEx;
    }

    @Override
    public float getFontSize() {
      PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
      if (ConsoleViewUtil.isConsoleViewEditor(myEditorEx)) {
        return propertyComponent.getFloat(FONT_SIZE_TO_RESET_CONSOLE, -1);
      }
      return propertyComponent.getFloat(FONT_SIZE_TO_RESET_EDITOR, -1);
    }

    @Override
    public void setFontSize(float fontSize) {
      EditorColorsManager.getInstance().getGlobalScheme().setEditorFontSize(fontSize);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorColorsManager.TOPIC).globalSchemeChange(null);
    }

    @Override
    public String getText(float fontSize) {
      return IdeBundle.message("action.reset.font.size.all.editors", fontSize);
    }
  }

  private static final class PresentationModeStrategy implements Strategy {
    private final UISettings settings = UISettings.getInstance();

    @Override
    public float getFontSize() {
      return settings.getPresentationModeFontSize();
    }

    @Override
    public void setFontSize(float fontSize) {
      int fs = (int)fontSize;
      settings.setPresentationModeFontSize(fs);
      for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
        if (editor instanceof EditorEx) {
          ((EditorEx)editor).setFontSize(fs);
        }
      }
    }

    @Override
    public String getText(float fontSize) {
      return IdeBundle.message("action.reset.font.size", fontSize);
    }
  }

  @ApiStatus.Internal
  public static Strategy getStrategy(EditorEx editor) {
    float globalSize = ConsoleViewUtil.isConsoleViewEditor(editor)
                       ? EditorColorsManager.getInstance().getGlobalScheme().getConsoleFontSize2D()
                       : EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize2D();

    if (editor instanceof EditorImpl) {
      if (UISettings.getInstance().getPresentationMode()) {
        return new PresentationModeStrategy();
      }
      if (((EditorImpl) editor).getFontSize2D() == globalSize) {
        return new AllEditorsStrategy(editor);
      }
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
      Strategy strategy = getStrategy(editorEx);
      float toReset = strategy.getFontSize();
      //noinspection DialogTitleCapitalization
      e.getPresentation().setText(strategy.getText(toReset));
      if (editor instanceof EditorImpl) {
        e.getPresentation().setEnabled(((EditorImpl)editor).getFontSize2D() != toReset);
      }
    }
  }

  private static class MyHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!(editor instanceof EditorEx)) {
        return;
      }
      getStrategy((EditorEx)editor).reset();
    }
  }

  private static final class Listener implements EditorColorsListener, ApplicationInitializedListener {
    @Override
    public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
      impl(false);
    }

    @Override
    public void componentsInitialized() {
      impl(true);
    }

    private static void impl(boolean force) {
      PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (force || !propertyComponent.getValue(PREVIOUS_COLOR_SCHEME, "").equals(globalScheme.getName())) {
        propertyComponent.setValue(PREVIOUS_COLOR_SCHEME, globalScheme.getName());
        propertyComponent.setValue(FONT_SIZE_TO_RESET_CONSOLE, globalScheme.getConsoleFontSize2D(), -1);
        propertyComponent.setValue(FONT_SIZE_TO_RESET_EDITOR, globalScheme.getEditorFontSize2D(), -1);
      }
    }
  }
}
