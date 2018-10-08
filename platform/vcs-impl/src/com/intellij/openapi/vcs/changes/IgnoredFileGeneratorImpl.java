// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class IgnoredFileGeneratorImpl implements IgnoredFileGenerator {

  private static final Logger LOG = Logger.getInstance(IgnoredFileGeneratorImpl.class);

  private static final String IGNORE_FILE_GENERATED_PROPERTY = "VCS_IGNOREFILE_GENERATED";

  private final Project myProject;

  private final Object myWriteLock = new Object();

  protected IgnoredFileGeneratorImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean generateFile(@NotNull AbstractVcs vcs) throws IOException {
    if (!needGenerateIgnoreFile(myProject)) {
      LOG.debug("Skip VCS ignore file generation");
      return false;
    }

    IgnoredFileContentProvider ignoredFileContentProvider = findIgnoredFileContentProvider(vcs);
    if (ignoredFileContentProvider == null) {
      LOG.debug("Cannot find content provider for vcs " + vcs.getName());
      return false;
    }

    synchronized (myWriteLock) {
      File ignoreFile = getIgnoreFile(ignoredFileContentProvider.getFileName());
      if (!ignoreFile.exists()) {
        String projectCharsetName = EncodingProjectManager.getInstance(myProject).getDefaultCharsetName();
        String ignoreFileContent = ignoredFileContentProvider.buildIgnoreFileContent(IgnoredFileProvider.IGNORE_FILE.getExtensions());
        FileUtil.writeToFile(ignoreFile, ignoreFileContent.getBytes(projectCharsetName));
        LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(ignoreFile));
        notifyAboutIgnoreFileGeneration(ignoreFile);
        return true;
      }
      return false;
    }
  }

  @Nullable
  private IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull AbstractVcs vcs) {
    IgnoredFileContentProvider[] contentProviders = IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.getExtensions(myProject);
    return Arrays.stream(contentProviders)
      .filter((provider) -> provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod()))
      .findFirst().orElse(null);
  }

  @NotNull
  private File getIgnoreFile(@NotNull String fileName) {
    @SystemIndependent String basePath = myProject.getBasePath();
    assert basePath != null : "Doesn't support default projects";

    return new File(basePath, fileName);
  }

  private void notifyAboutIgnoreFileGeneration(@NotNull File ignoreFile) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue(IGNORE_FILE_GENERATED_PROPERTY, true);
    VcsNotifier.getInstance(myProject)
      .notifyMinorInfo("",
                       VcsBundle.message("ignored.file.generation.message", ignoreFile.getName()),
                       NotificationAction.create(VcsBundle.message("ignored.file.generation.review"), (event, notification) -> {
                         notification.expire();
                         VirtualFile ignoreVirtualFile = VfsUtil.findFileByIoFile(ignoreFile, true);
                         if (ignoreVirtualFile != null) {
                           new OpenFileDescriptor(myProject, ignoreVirtualFile).navigate(true);
                         }
                         else {
                           LOG.warn("Cannot find ignore file " + ignoreFile.getName());
                         }
                       }));
  }

  private static boolean needGenerateIgnoreFile(@NotNull Project project) {
    boolean wasGeneratedPreviously = PropertiesComponent.getInstance(project).getBoolean(IGNORE_FILE_GENERATED_PROPERTY, false);
    LOG.debug("Ignore file generated previously " + wasGeneratedPreviously);
    boolean needGenerateRegistryFlag = Registry.is("vcs.ignorefile.generation", true);
    return !wasGeneratedPreviously && needGenerateRegistryFlag;
  }
}
