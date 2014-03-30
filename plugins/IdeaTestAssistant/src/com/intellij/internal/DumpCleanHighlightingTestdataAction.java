/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExpectedHighlightingData;

import java.io.File;
import java.io.IOException;

public class DumpCleanHighlightingTestdataAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#" + DumpCleanHighlightingTestdataAction.class);

  public DumpCleanHighlightingTestdataAction() {
    super("Dump highlighting-markup-free data");
  }

  @Override
  public void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document != null) {
          final ExpectedHighlightingData data = new ExpectedHighlightingData(document, true, true);
          data.init();
        }
        return;
      }
    }
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle("Choose Directory");
    descriptor.setDescription("Directory containing highlighting test data");
    final VirtualFile dirToProcess = FileChooser.chooseFile(descriptor, project, null);
    if (dirToProcess != null) {
      LOG.assertTrue(project != null);
      final FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      targetDescriptor.setTitle("Choose Directory");
      targetDescriptor.setDescription("Directory where highlighting-markup-free copies would be placed");
      final VirtualFile destinationFolder = FileChooser.chooseFile(targetDescriptor, project, null);
      if (destinationFolder == dirToProcess) {
        Messages.showErrorDialog(project, "Source and destination roots should differ", "Reject to Proceed");
        return;
      }
      if (destinationFolder != null) {
        final File destination = VfsUtilCore.virtualToIoFile(destinationFolder);
        final VirtualFile[] files = dirToProcess.getChildren();
        for (VirtualFile virtualFile : files) {
          final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
          if (document != null) {
            final ExpectedHighlightingData data = new ExpectedHighlightingData(document, true, true);
            data.init();
            final File file = new File(destination, virtualFile.getName());
            try {
              FileUtil.writeToFile(file, document.getText());
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(CommonDataKeys.PROJECT.getData(e.getDataContext()) != null);
  }
}