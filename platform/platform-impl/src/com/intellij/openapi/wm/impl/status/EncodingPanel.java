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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeEncodingUpdateGroup;
import com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManager;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;

/**
 * @author cdr
 */
public class EncodingPanel extends TextPanel implements StatusBarPatch {
  public EncodingPanel(final StatusBarImpl statusBar) {
    super(false, "windows-1251");
    StatusBarTooltipper.install(this, statusBar);
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        EncodingPanel.this.mouseClicked(statusBar);
      }
    });
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    boolean enabled;
    String text;
    if (selected != null) {
      VirtualFile file = getSelectedFile(selected);
      Pair<String,Boolean> result = ChangeEncodingUpdateGroup.update(file);
      text = result.getFirst();
      enabled = result.getSecond();
      if (file != null) {
        Charset charset = ChooseFileEncodingAction.charsetFromContent(file);
        if (charset == null) charset = file.getCharset();
        setText(charset.displayName());
      }
    }
    else {
      text = "";
      enabled = false;
      setText("");
    }
    setEnabled(enabled);
    return text;
  }

  private static VirtualFile getSelectedFile(final Editor selected) {
    if (selected == null) return null;
    Document document = selected.getDocument();
    return FileDocumentManager.getInstance().getFile(document);
  }

  public void clear() {
    setText("");
  }

  private Editor getEditor() {
    final Project project = getProject();
    if (project == null) return null;
    return getEditor(project);
  }

  private static Editor getEditor(final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }

  private void mouseClicked(final StatusBarImpl statusBar) {
    VirtualFile virtualFile = getSelectedFile(getEditor());
    if (virtualFile == null) return;
    if (!isEnabled()) return;

    ChooseFileEncodingAction changeAction = new ChooseFileEncodingAction(virtualFile) {
      protected void chosen(final VirtualFile virtualFile, final Charset charset) {
        EncodingManager.getInstance().setEncoding(virtualFile, charset);
      }
    };
    DataContext context = DataManager.getInstance().getDataContext(statusBar);
    DataContext dataContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.VIRTUAL_FILE.getName(), virtualFile,
                              SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), getProject(), context));
    Presentation presentation = changeAction.getTemplatePresentation();
    AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, presentation, ActionManager.getInstance(), 0);
    changeAction.update(event);

    DefaultActionGroup group = changeAction.createGroup(false);

    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext,
                                                                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
                                                                                null,
                                                                                30);
    popup.showUnderneathOf(this);
  }
}
