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
package com.intellij.cvsSupport2.actions;

import com.intellij.CommonBundle;
import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.util.ui.OptionsDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author lesya
 */
public class RemoveLocallyFileOrDirectoryAction extends ActionOnSelectedElement {
  private final Options myOptions;

  private RemoveLocallyFileOrDirectoryAction(Options options) {
    super(false);
    myOptions = options;
  }

  public static RemoveLocallyFileOrDirectoryAction createAutomaticallyAction() {
    return new RemoveLocallyFileOrDirectoryAction(Options.ON_FILE_REMOVING);
  }

  public RemoveLocallyFileOrDirectoryAction() {
    this(Options.REMOVE_ACTION);
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    final Project project = context.getProject();
    final boolean showDialog = myOptions.isToBeShown(project) || OptionsDialog.shiftIsPressed(context.getModifiers());
    return getCvsHandler(project, getFilesToRemove(context), showDialog);
  }

  public static CvsHandler getDefaultHandler(Project project, Collection<File> files) {
    return getCvsHandler(project, files, false);
  }

  private static CvsHandler getCvsHandler(final Project project,
                                          final Collection<File> filesToRemove,
                                          final boolean showDialog) {
    final ArrayList<File> files = new ArrayList<>();

    for (final File file : filesToRemove) {
      if (CvsUtil.fileIsLocallyAdded(file)) {
        CvsUtil.removeEntryFor(file);
      }
      else {
        files.add(file);
      }
    }

    if (files.isEmpty()) return CvsHandler.NULL;
    Collection<FilePath> filesToBeRemoved = filesToFilePaths(files);
    if (showDialog) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
      filesToBeRemoved = vcsHelper.selectFilePathsToProcess(filesToFilePaths(files),
                                                            CvsBundle.message("dialog.title.delete.files.from.cvs"),
                                                            null,
                                                            CvsBundle.message("dialog.title.delete.file.from.cvs"),
                                                            CvsBundle.message("confirmation.text.delete.file.from.cvs"),
                                                            VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION,
                                                            CvsBundle.message("button.text.delete.from.cvs"),
                                                            CommonBundle.getCancelButtonText());
      if (filesToBeRemoved == null || filesToBeRemoved.isEmpty()) return CvsHandler.NULL;
    }
    return CommandCvsHandler.createRemoveFilesHandler(project, ChangesUtil.filePathsToFiles(filesToBeRemoved));
  }

  private static List<FilePath> filesToFilePaths(final ArrayList<File> files) {
    final List<FilePath> result = new ArrayList<>();
    for(File f: files) {
      result.add(VcsContextFactory.SERVICE.getInstance().createFilePathOnDeleted(f, false));
    }
    return result;
  }

  protected Collection<File> getFilesToRemove(CvsContext context) {
    final Collection<String> deletedFileNames = context.getDeletedFileNames();
    final ArrayList<File> result = new ArrayList<>();
    for (final String deletedFileName : deletedFileNames) {
      result.add(new File(deletedFileName));
    }
    return result;
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.mark.as.deleted");
  }
}
