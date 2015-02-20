/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
class HttpFileEditor extends BaseRemoteFileEditor {
  private final RemoteFilePanel myPanel;

  public HttpFileEditor(@NotNull Project project, @NotNull HttpVirtualFile virtualFile) {
    super(project);

    myPanel = new RemoteFilePanel(project, virtualFile, this);
    RemoteFileInfo fileInfo = virtualFile.getFileInfo();
    assert fileInfo != null;
    fileInfo.download()
      .done(new Consumer<VirtualFile>() {
        @Override
        public void consume(VirtualFile file) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              contentLoaded();
            }
          }, myProject.getDisposed());
        }
      })
      .rejected(new Consumer<Throwable>() {
        @Override
        public void consume(Throwable throwable) {
          contentRejected();
        }
      });
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getPreferredFocusedComponent();
    }
    return myPanel.getMainPanel();
  }

  @Override
  @NotNull
  public String getName() {
    return "Http";
  }

  @Override
  public void selectNotify() {
    myPanel.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myPanel.deselectNotify();
  }

  @Override
  @Nullable
  protected TextEditor getTextEditor() {
    return myPanel.getFileEditor();
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel.dispose();
  }
}
