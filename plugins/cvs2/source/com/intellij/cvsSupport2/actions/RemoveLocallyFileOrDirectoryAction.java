package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.ui.AbstractDeleteConfirmationDialog;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.CvsBundle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
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
    Project project = context.getProject();
    final boolean showDialog = myOptions.isToBeShown(project) || OptionsDialog.shiftIsPressed(context.getModifiers());

    return getCvsHandler(project, getFilesToRemove(context), showDialog, myOptions);
  }

  public static CvsHandler getDefaultHandler(Project project, Collection<File> files) {
    return getCvsHandler(project, files, true, Options.NULL);
  }

  private static CvsHandler getCvsHandler(final Project project,
                                          final Collection<File> filesToRemove,
                                          final boolean showDialog,
                                          final Options dialogOptions) {
    ArrayList<File> files = new ArrayList<File>();

    for (final File file : filesToRemove) {
      if (CvsUtil.fileIsLocallyAdded(file)) {
        CvsUtil.removeEntryFor(file);
      }
      else {
        files.add(file);
      }
    }

    if (files.isEmpty()) return CvsHandler.NULL;
    Collection<File> filesToBeRemoved = files;
    if (showDialog) {
      AbstractDeleteConfirmationDialog dialog = AbstractDeleteConfirmationDialog.createDialog(project,
                                                                                              files,
                                                                                              dialogOptions);

      dialog.show();
      if (!dialog.isOK()) return CvsHandler.NULL;
      if (dialog.getFilesToRemove().isEmpty()) return CvsHandler.NULL;
      filesToBeRemoved = dialog.getFilesToRemove();
    }
    return CommandCvsHandler.createRemoveFilesHandler(project, filesToBeRemoved);
  }

  protected Collection<File> getFilesToRemove(CvsContext context) {
    Collection<String> deletedFileNames = context.getDeletedFileNames();
    ArrayList<File> result = new ArrayList<File>();
    for (final String deletedFileName : deletedFileNames) {
      result.add(new File(deletedFileName));
    }
    return result;
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.mark.as.deleted");
  }
}
