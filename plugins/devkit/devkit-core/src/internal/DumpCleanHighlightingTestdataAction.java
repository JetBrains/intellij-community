// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal;

import com.intellij.CommonBundle;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.io.File;
import java.io.IOException;

public class DumpCleanHighlightingTestdataAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(DumpCleanHighlightingTestdataAction.class);

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
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
    descriptor.setTitle(DevKitBundle.message("action.DumpCleanTestData.file.chooser.title"));
    descriptor.setDescription(DevKitBundle.message("action.DumpCleanTestData.file.chooser.source.description"));
    final VirtualFile dirToProcess = FileChooser.chooseFile(descriptor, project, null);
    if (dirToProcess != null) {
      LOG.assertTrue(project != null);
      final FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      targetDescriptor.setTitle(DevKitBundle.message("action.DumpCleanTestData.file.chooser.title"));
      targetDescriptor.setDescription(DevKitBundle.message("action.DumpCleanTestData.file.chooser.destination.description"));
      final VirtualFile destinationFolder = FileChooser.chooseFile(targetDescriptor, project, null);
      if (dirToProcess.equals(destinationFolder)) {
        Messages.showErrorDialog(project, DevKitBundle.message("action.DumpCleanTestData.error.source.destination.must.differ"),
                                 CommonBundle.getErrorTitle());
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
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }
}