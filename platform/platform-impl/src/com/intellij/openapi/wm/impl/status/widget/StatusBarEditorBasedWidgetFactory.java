// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StatusBarEditorBasedWidgetFactory implements StatusBarWidgetFactory {
  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return getTextEditor(statusBar) != null;
  }

  @Nullable
  protected FileEditor getFileEditor(@NotNull StatusBar statusBar) {
    return StatusBarUtil.getCurrentFileEditor(statusBar);
  }

  @Nullable
  protected Editor getTextEditor(@NotNull StatusBar statusBar) {
    return StatusBarUtil.getCurrentTextEditor(statusBar);
  }
}
