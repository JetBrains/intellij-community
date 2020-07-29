// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl;

import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.EditAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

public final class ConversionResultImpl implements ConversionResult {
  public static final ConversionResultImpl CONVERSION_NOT_NEEDED = new ConversionResultImpl(false, false, false);
  public static final ConversionResultImpl CONVERSION_CANCELED = new ConversionResultImpl(true, true, false);
  public static final ConversionResultImpl ERROR_OCCURRED = new ConversionResultImpl(true, false, true);
  private final boolean myConversionNeeded;
  private final boolean myConversionCanceled;
  private final boolean myErrorOccurred;
  private final Set<Path> myChangedFiles = new HashSet<>();
  private final Set<Path> myCreatedFiles = new HashSet<>();

  public ConversionResultImpl(boolean conversionNeeded, boolean conversionCanceled, boolean errorOccurred) {
    myConversionNeeded = conversionNeeded;
    myConversionCanceled = conversionCanceled;
    myErrorOccurred = errorOccurred;
  }

  public ConversionResultImpl(List<? extends ConversionRunner> converters) {
    this(true, false, false);
    for (ConversionRunner converter : converters) {
      myChangedFiles.addAll(converter.getAffectedFiles());
      myCreatedFiles.addAll(converter.getCreatedFiles());
    }
  }

  @Override
  public boolean conversionNotNeeded() {
    return !myConversionNeeded;
  }

  @Override
  public boolean openingIsCanceled() {
    return myConversionCanceled || myErrorOccurred;
  }

  @Override
  public void postStartupActivity(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (app.isHeadlessEnvironment() || app.isUnitTestMode()) {
      return;
    }

    List<VirtualFile> changedFiles = findVirtualFiles(myChangedFiles);
    if (!changedFiles.isEmpty()) {
      EditAction.editFilesAndShowErrors(project, changedFiles);
    }

    List<VirtualFile> createdFiles = findVirtualFiles(myCreatedFiles);
    if (!containsFilesUnderVcs(createdFiles, project)) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      Collection<VirtualFile> selected = AbstractVcsHelper.getInstance(project)
        .selectFilesToProcess(createdFiles, VcsBundle.message("dialog.title.files.created"),
                              VcsBundle.message("label.select.files.to.be.added.to.version.control"), null, null,
                              VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION);
      if (selected != null && !selected.isEmpty()) {
        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
        changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), new ArrayList<>(selected));
      }
    }, ModalityState.NON_MODAL, project.getDisposed());
  }

  private static boolean containsFilesUnderVcs(@NotNull List<VirtualFile> files, Project project) {
    for (VirtualFile file : files) {
      if (ChangesUtil.getVcsForFile(file, project) != null) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<VirtualFile> findVirtualFiles(@NotNull Collection<Path> ioFiles) {
    List<VirtualFile> files = new ArrayList<>(ioFiles.size());
    for (Path file : ioFiles) {
      ContainerUtil.addIfNotNull(files, LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString())));
    }
    return files;
  }
}
