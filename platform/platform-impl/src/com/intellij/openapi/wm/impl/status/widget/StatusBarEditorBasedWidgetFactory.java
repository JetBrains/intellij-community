// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
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
