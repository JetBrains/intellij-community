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
package com.intellij.impl;

import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsShowConfirmationOptionImpl;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.EditAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class ConversionResultImpl implements ConversionResult {
  public static final ConversionResultImpl CONVERSION_NOT_NEEDED = new ConversionResultImpl(false, false, false);
  public static final ConversionResultImpl CONVERSION_CANCELED = new ConversionResultImpl(true, true, false);
  public static final ConversionResultImpl ERROR_OCCURRED = new ConversionResultImpl(true, false, true);
  private final boolean myConversionNeeded;
  private final boolean myConversionCanceled;
  private final boolean myErrorOccurred;
  private final Set<File> myChangedFiles = new HashSet<>();
  private final Set<File> myCreatedFiles = new HashSet<>();

  public ConversionResultImpl(boolean conversionNeeded, boolean conversionCanceled, boolean errorOccurred) {
    myConversionNeeded = conversionNeeded;
    myConversionCanceled = conversionCanceled;
    myErrorOccurred = errorOccurred;
  }

  public ConversionResultImpl(List<ConversionRunner> converters) {
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
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      return;
    }

    List<VirtualFile> changedFiles = findVirtualFiles(myChangedFiles);
    if (!changedFiles.isEmpty()) {
      EditAction.editFilesAndShowErrors(project, changedFiles);
    }

    final List<VirtualFile> createdFiles = findVirtualFiles(myCreatedFiles);
    if (containsFilesUnderVcs(createdFiles, project)) {
      final VcsShowConfirmationOptionImpl option = new VcsShowConfirmationOptionImpl("", "", "", "", "");
      final Collection<VirtualFile> selected = AbstractVcsHelper.getInstance(project)
        .selectFilesToProcess(createdFiles, "Files Created", "Select files to be added to version control", null, null, option);
      if (selected != null && !selected.isEmpty()) {
        final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
        changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), new ArrayList<>(selected));
      }
    }
  }

  private static boolean containsFilesUnderVcs(List<VirtualFile> files, Project project) {
    for (VirtualFile file : files) {
      if (ChangesUtil.getVcsForFile(file, project) != null) {
        return true;
      }
    }
    return false;
  }

  private static List<VirtualFile> findVirtualFiles(Collection<File> ioFiles) {
    List<VirtualFile> files = new ArrayList<>();
    for (File file : ioFiles) {
      ContainerUtil.addIfNotNull(files, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
    }
    return files;
  }
}
