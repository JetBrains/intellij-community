/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/22/11
 * Time: 3:04 PM
 */
public abstract class FileAndDocumentListenersForShortDiff {
  private final ShortDiffDetails myDiffDetails;
  private final FileAndDocumentListenersForShortDiff.MyFileListener myFileListener;
  private final FileAndDocumentListenersForShortDiff.MyDocumentListener myDocumentListener;

  protected FileAndDocumentListenersForShortDiff(final ShortDiffDetails diffDetails) {
    myDiffDetails = diffDetails;
    myFileListener = new MyFileListener();
    myDocumentListener = new MyDocumentListener();
  }

  public void on() {
    VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener);
  }

  public void off() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
  }

  protected abstract void updateDetails();
  protected abstract boolean updateSynchronously();

  private class MyFileListener extends VirtualFileAdapter {
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      impl(event.getFile());
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      impl(event.getFile());
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      impl(event.getFile());
    }
  }

  private void impl(final VirtualFile vf) {
    final boolean wasInCache = myDiffDetails.refreshData(vf);
    final FilePath filePath = myDiffDetails.getCurrentFilePath();
    if (wasInCache || (filePath != null && filePath.getVirtualFile() != null && filePath.getVirtualFile().equals(vf))) {
      updateDetails();
    }
  }

  private class MyDocumentListener implements DocumentListener {
    private final FileDocumentManager myFileDocumentManager;

    public MyDocumentListener() {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
    }

    @Override
    public void documentChanged(DocumentEvent event) {
      final VirtualFile vf = myFileDocumentManager.getFile(event.getDocument());
      if (vf != null) {
        final FilePath filePath = myDiffDetails.getCurrentFilePath();
        if (filePath != null && filePath.getVirtualFile() != null && filePath.getVirtualFile().equals(vf)) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (! updateSynchronously()) {
                impl(vf);
              }
            }
          });
        } else {
          impl(vf);
        }
      }
    }
  }
}
