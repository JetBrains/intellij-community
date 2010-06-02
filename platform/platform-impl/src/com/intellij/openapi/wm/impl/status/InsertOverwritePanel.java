/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.awt.event.*;

/**
 * @author cdr
 */
public class InsertOverwritePanel implements StatusBarWidget, StatusBarWidget.TextPresentation {
  private StatusBar myStatusBar;

  @NotNull
  public String ID() {
    return "InsertOverwrite";
  }

  public WidgetPresentation getPresentation(@NotNull Type type) {
    return this;
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  public void dispose() {
    myStatusBar = null;
  }

  @NotNull
  public String getText() {
    final Editor editor = getEditor();
    if (editor != null) {
      return editor.isColumnMode()
             ? UIBundle.message("status.bar.column.status.text")
             : editor.isInsertMode()
               ? UIBundle.message("status.bar.insert.status.text")
               : UIBundle.message("status.bar.overwrite.status.text");

    }

    return "";
  }

  @NotNull
  public String getMaxPossibleText() {
    return UIBundle.message("status.bar.overwrite.status.text");
  }

  public String getTooltipText() {
    return null;
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return null;
  }

  @Nullable
  private Editor getEditor() {
    final Project project = getProject();
    if (project != null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      return manager.getSelectedTextEditor();
    }

    return null;
  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component) myStatusBar));
  }
}
