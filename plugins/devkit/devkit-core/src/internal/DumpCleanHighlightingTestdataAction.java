// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.internal;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

final class DumpCleanHighlightingTestdataAction extends AnAction implements DumbAware, UpdateInBackground {
  private static final Logger LOG = Logger.getInstance(DumpCleanHighlightingTestdataAction.class);

  private final NullableLazyValue<Class<?>> myHighlightingDataClass = lazyNullable(() -> {
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
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (psiFile != null) {
      processFile(psiFile);
    }
    else {
      processDirectory(project);
    }
  }

  private void processFile(PsiFile psiFile) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        stripHighlightingData(document);
      }
    }
  }

  private void processDirectory(Project project) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(DevKitBundle.message("action.DumpCleanTestData.file.chooser.title"));
    descriptor.setDescription(DevKitBundle.message("action.DumpCleanTestData.file.chooser.source.description"));
    VirtualFile dirToProcess = FileChooser.chooseFile(descriptor, project, null);
    if (dirToProcess == null) return;

    descriptor.setDescription(DevKitBundle.message("action.DumpCleanTestData.file.chooser.destination.description"));
    VirtualFile destinationDir = FileChooser.chooseFile(descriptor, project, null);
    if (destinationDir == null) return;
    if (dirToProcess.equals(destinationDir)) {
      Messages.showErrorDialog(project, DevKitBundle.message("action.DumpCleanTestData.error.source.destination.must.differ"), CommonBundle.getErrorTitle());
      return;
    }

    Path destination = destinationDir.toNioPath();
    for (VirtualFile file : dirToProcess.getChildren()) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        stripHighlightingData(document);
        try {
          Files.writeString(destination.resolve(file.getName()), document.getText(), file.getCharset());
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  /** {@code new ExpectedHighlightingData(document, true, true).init()} */
  private void stripHighlightingData(Document document) {
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
