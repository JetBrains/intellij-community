/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
    super("Dump Highlighting-markup-free Data");
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getProject();
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
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
      if (dirToProcess.equals(destinationFolder)) {
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
            catch (IOException ex) {
              LOG.error(ex);
            }
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }
}