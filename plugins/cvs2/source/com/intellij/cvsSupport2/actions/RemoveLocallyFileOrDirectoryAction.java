package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.ui.AbstractDeleteConfirmationDialog;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.ui.AbstractDeleteConfirmationDialog;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.OptionsDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

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
    Collection<File> filesToRemove = getFilesToRemove(context);

    ArrayList<File> files = new ArrayList<File>();

    for (Iterator each = filesToRemove.iterator(); each.hasNext();) {
      File file = (File)each.next();
      if (CvsUtil.fileIsLocallyAdded(file)) {
        CvsUtil.removeEntryFor(file);
      }
      else {
        files.add(file);
      }
    }

    if (files.isEmpty()) return CvsHandler.NULL;
    Project project = context.getProject();
    Collection<File> filesToBeRemoved = files;
    if (myOptions.isToBeShown(project)  || OptionsDialog.shiftIsPressed(context.getModifiers())) {
      AbstractDeleteConfirmationDialog dialog = AbstractDeleteConfirmationDialog.createDialog(project,
                                                                                              files,
                                                                                              myOptions);

      dialog.show();
      if (!dialog.isOK()) return CvsHandler.NULL;
      if (dialog.getFilesToRemove().isEmpty()) return CvsHandler.NULL;
      filesToBeRemoved = dialog.getFilesToRemove();
    }
    return CommandCvsHandler.createRemoveFilesHandler(filesToBeRemoved);
  }

  protected Collection<File> getFilesToRemove(CvsContext context) {
    Collection deletedFileNames = context.getDeletedFileNames();
    ArrayList<File> result = new ArrayList<File>();
    for (Iterator each = deletedFileNames.iterator(); each.hasNext();) {
      result.add(new File((String)each.next()));
    }
    return result;
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.mark.as.deleted");
  }
}
