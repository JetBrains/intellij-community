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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;

/**
 * @author cdr
 */
public class EncodingPanel implements StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {
  private StatusBar myStatusBar;
  private String mySelected;

  public EncodingPanel(@NotNull final Project project) {
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        update();
      }

      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        update();
      }
    });
  }

  @NotNull
  public String ID() {
    return "Encoding";
  }

  public WidgetPresentation getPresentation(@NotNull Type type) {
    return this;
  }

  public void dispose() {
    myStatusBar = null;
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  @NotNull
  public String getMaxValue() {
    return "windows-1251";
  }

  @NotNull
  public ListPopup getPopupStep() {
    final DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    final DataContext dataContext =
      SimpleDataContext.getSimpleContext(PlatformDataKeys.VIRTUAL_FILE.getName(), getSelectedFile(),
                                         SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), getProject(), parent));
    return JBPopupFactory.getInstance().createActionGroupPopup(null, new ChooseFileEncodingAction(getSelectedFile()) {
      @Override
      protected void chosen(VirtualFile virtualFile, Charset charset) {
        if (virtualFile != null) {
          EncodingManager.getInstance().setEncoding(virtualFile, charset);
          EncodingPanel.this.update();
        }
      }
    }.createGroup(false), dataContext, false, false, false, null, 30, null);
  }

  public String getTooltipText() {
    return null;
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return null;
  }

  private void setSelectedValue(@NotNull final Charset charset) {
    mySelected = charset.displayName();
  }

  public String getSelectedValue() {
    return mySelected;
  }

  private void update() {
    final VirtualFile file = getSelectedFile();
    if (file != null) {
      Charset charset = ChooseFileEncodingAction.charsetFromContent(file);
      if (charset == null) charset = file.getCharset();
      setSelectedValue(charset);
      myStatusBar.updateWidget(ID());
    }
  }

  @Nullable
  private VirtualFile getSelectedFile() {
    final Editor editor = getEditor();
    if (editor == null) return null;
    Document document = editor.getDocument();
    return FileDocumentManager.getInstance().getFile(document);
  }

  @Nullable
  private Editor getEditor() {
    final Project project = getProject();
    if (project == null) return null;
    return getEditor(project);
  }

  @Nullable
  private static Editor getEditor(@NotNull final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component)myStatusBar));
  }
}
