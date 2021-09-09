// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class DumpCleanHighlightingTestdataAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(DumpCleanHighlightingTestdataAction.class);

  private final NullableLazyValue<Class<?>> myHighlightingDataClass = NullableLazyValue.createValue(() -> {
    try {
      Path jar = Path.of(PathManager.getLibPath(), "testFramework.jar");
      if (Files.exists(jar)) {
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
        return Class.forName("com.intellij.testFramework.ExpectedHighlightingData", false, loader);
      }
      else {
        return Class.forName("com.intellij.testFramework.ExpectedHighlightingData");
      }
    }
    catch (ClassNotFoundException | MalformedURLException e) {
      LOG.error("'ExpectedHighlightingData' class not found, action disabled", e);
      return null;
    }
  });

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null && myHighlightingDataClass.getValue() != null);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document != null) {
          initHighlightingData(document);
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
            initHighlightingData(document);
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

  /** {@code new ExpectedHighlightingData(document, true, true).init()} */
  private void initHighlightingData(Document document) {
    Class<?> klass = myHighlightingDataClass.getValue();
    if (klass != null) {
      try {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle ctor = lookup.findConstructor(klass, MethodType.methodType(void.class, Document.class, boolean.class, boolean.class));
        Object instance = ctor.invoke(document, true, true);
        lookup.findVirtual(klass, "init", MethodType.methodType(void.class)).invoke(instance);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }
  }
}
