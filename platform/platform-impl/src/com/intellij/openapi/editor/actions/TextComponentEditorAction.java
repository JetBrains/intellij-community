// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;


public abstract class TextComponentEditorAction extends EditorAction {
  static {
    TextComponentEditorImpl.ensureRequiredClassesAreLoaded();
  }

  private final boolean allowSpeedSearch;

  protected TextComponentEditorAction(@NotNull EditorActionHandler defaultHandler) {
    this(defaultHandler, true);
  }

  protected TextComponentEditorAction(@NotNull EditorActionHandler defaultHandler, boolean allowSpeedSearch) {
    super(defaultHandler);
    this.allowSpeedSearch = allowSpeedSearch;
    ensureHandlerChainIsLoaded();
  }

  private void ensureHandlerChainIsLoaded() {
    getHandler().runForAllCarets(); // triggers DynamicEditorActionHandler.getHandlerChain
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected @Nullable Editor getEditor(@NotNull DataContext dataContext) {
    return getEditorFromContext(dataContext, allowSpeedSearch);
  }

  public static @Nullable Editor getEditorFromContext(@NotNull DataContext dataContext) {
    return getEditorFromContext(dataContext, true);
  }

  private static @Nullable Editor getEditorFromContext(@NotNull DataContext dataContext, boolean allowSpeedSearch) {
    // try to get host editor in case of injections during action update in EDT
    Editor editor = EDT.isCurrentThreadEdt() && !SlowOperations.isInsideActivity(SlowOperations.ACTION_PERFORM) ?
                    CommonDataKeys.HOST_EDITOR.getData(dataContext) : null;
    if (editor == null) {
      editor = CommonDataKeys.EDITOR.getData(dataContext);
    }
    if (editor != null) return editor;
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Object data = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    if (data instanceof EditorComponentImpl) {
      // can happen if editor is already disposed, or if it's in renderer mode
      return null;
    }
    if (data instanceof JTextComponent) {
      return new TextComponentEditorImpl(project, (JTextComponent)data);
    }
    if (allowSpeedSearch && data instanceof JComponent) {
      JTextField field = findActiveSpeedSearchTextField((JComponent)data);
      if (field != null) {
        return new TextComponentEditorImpl(project, field);
      }
    }
    return null;
  }

  private static JTextField findActiveSpeedSearchTextField(JComponent c) {
    SpeedSearchSupply supply = SpeedSearchSupply.getSupply(c);
    if (supply instanceof SpeedSearchBase) {
      return ((SpeedSearchBase<?>)supply).getSearchField();
    }
    if (c instanceof DataProvider) {
      Object component = PlatformDataKeys.SPEED_SEARCH_COMPONENT.getData((DataProvider)c);
      if (component instanceof JTextField) {
        return (JTextField)component;
      }
    }
    return null;
  }
}