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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 * @author max
 */
public class BinaryDiffTool implements DiffTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.BinaryDiffTool");
  public static final DiffTool INSTANCE = new BinaryDiffTool();

  public void show(final DiffRequest data) {
    final DiffContent current = data.getContents()[0];
    final DiffContent upToDate = data.getContents()[1];
    final Project project = data.getProject();
    if ((current instanceof FileContent && upToDate instanceof FileContent)) {
      final VirtualFile src = current.getFile();
      final VirtualFile trg = upToDate.getFile();
      if (src != null && trg != null) {
        final FileEditorProvider[] srcProvider = FileEditorProviderManager.getInstance().getProviders(project, src);
        final FileEditorProvider[] trgProvider = FileEditorProviderManager.getInstance().getProviders(project, trg);
        if (srcProvider.length > 0 && trgProvider.length > 0) {
          new DialogWrapper(project) {
            public DiffPanel myPanel;
            {
              setModal(false);
              init();
            }

            @Override
            protected String getDimensionServiceKey() {
              return "BinaryDiffDialog";
            }

            @Override
            protected Action[] createActions() {
              final Action close = getCancelAction();
              close.putValue(Action.NAME, "&Close");
              return new Action[]{close};
            }

            @Override
            protected void dispose() {
              super.dispose();
              Disposer.dispose(myPanel);
            }

            @Override
            protected JComponent createCenterPanel() {
              myPanel = DiffManager.getInstance().createDiffPanel(getWindow(), project);
              myPanel.setDiffRequest(data);
              myPanel.setTitle1(src.getPath());
              myPanel.setTitle2(trg.getPath());
              return myPanel.getComponent();
            }
          }.show();
          return;
        }
      }
    }
    try {
      final boolean equal = Arrays.equals(current.getBytes(), upToDate.getBytes());
      Messages.showMessageDialog(equal
                                 ? DiffBundle.message("binary.files.are.identical.message")
                                 : DiffBundle.message("binary.files.are.different.message"),
                                 equal
                                 ? DiffBundle.message("files.are.identical.dialog.title")
                                 : DiffBundle.message("files.are.different.dialog.title"),
                                 Messages.getInformationIcon());
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  public boolean canShow(final DiffRequest data) {
    final DiffContent[] contents = data.getContents();
    if (contents.length != 2) {
      return false;
    }
    for (DiffContent content : contents) {
      final VirtualFile file = content.getFile();
      if (file == null || file.isDirectory()) {
        return false;
      }
    }
    return true;
  }
}
