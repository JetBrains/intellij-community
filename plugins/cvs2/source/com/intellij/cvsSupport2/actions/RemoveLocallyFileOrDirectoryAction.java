package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.peer.PeerFactory;
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
    Project project = context.getProject();
    final boolean showDialog = myOptions.isToBeShown(project) || OptionsDialog.shiftIsPressed(context.getModifiers());

    return getCvsHandler(project, getFilesToRemove(context), showDialog);
  }

  public static CvsHandler getDefaultHandler(Project project, Collection<File> files) {
    return getCvsHandler(project, files, true);
  }

  private static CvsHandler getCvsHandler(final Project project,
                                          final Collection<File> filesToRemove,
                                          final boolean showDialog) {
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
    Collection<FilePath> filesToBeRemoved = filesToFilePaths(files);
    if (showDialog) {
      final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
      filesToBeRemoved = vcsHelper.selectFilePathsToProcess(filesToFilePaths(files),
                                                            CvsBundle.message("dialog.title.delete.files.from.cvs"),
                                                            null,
                                                            CvsBundle.message("dialog.title.delete.file.from.cvs"),
                                                            CvsBundle.message("confirmation.text.delete.file.from.cvs"),
                                                            CvsVcs2.getInstance(project).getRemoveConfirmation());
      if (filesToBeRemoved == null || filesToBeRemoved.isEmpty()) return CvsHandler.NULL;
    }
    return CommandCvsHandler.createRemoveFilesHandler(project, ChangesUtil.filePathsToFiles(filesToBeRemoved));
  }

  private static List<FilePath> filesToFilePaths(final ArrayList<File> files) {
    List<FilePath> result = new ArrayList<FilePath>();
    for(File f: files) {
      result.add(PeerFactory.getInstance().getVcsContextFactory().createFilePathOnDeleted(f, false));
    }
    return result;
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
