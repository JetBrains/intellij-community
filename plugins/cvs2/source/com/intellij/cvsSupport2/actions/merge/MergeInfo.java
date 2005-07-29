package com.intellij.cvsSupport2.actions.merge;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperaton;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SelectFromListDialog;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 14, 2005
 * Time: 5:44:14 PM
 * To change this template use File | Settings | File Templates.
 */
class MergeInfo implements MergeDataProvider{
  private final boolean myUseLocal;
  private final String myResultRevision;
  private final String myOriginalRevision;
  private final String myLastRevision;

  private final VirtualFile myFile;
  private final Project myProject;

  public MergeInfo(final boolean useLocal,
                   final String resultRevision,
                   final String originalRevision,
                   final String lastRevision,
                   final VirtualFile file,
                   final Project project) {
    myUseLocal = useLocal;
    myResultRevision = resultRevision;
    myOriginalRevision = originalRevision;
    myLastRevision = lastRevision;
    myFile = file;
    myProject = project;
  }

  public boolean isUseStoredRevision() {
    return myUseLocal;
  }

  public String getOriginalRevision() {
    return myOriginalRevision;
  }

  public String getLastRevision() {
    return myLastRevision;
  }

  public String getResultRevision() {
    return myResultRevision;
  }

  public MergeData createData() throws VcsException {
    return loadRevisionsInternal(getOriginalRevision(),
                                 getLastRevision());
  }

  protected VirtualFile[] getStoredFiles() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    String prefix = ".#" + myFile.getName() + ".";
    VirtualFile[] children = myFile.getParent().getChildren();
    if (children != null) {
      for (VirtualFile child : children) {
        if (child.getName().startsWith(prefix)) result.add(child);
      }
    }

    return result.toArray(new VirtualFile[result.size()]);
  }


  public byte[] getStoredContent() throws VcsException {
    try {
      if (isUseStoredRevision()) {
        String revision = getResultRevision();

        final byte[] result = CvsUtil.getStoredContentForFile(myFile, revision);

        if (result != null) return result;

        VirtualFile[] storedFiles = getStoredFiles();

        if (storedFiles.length == 0) {
          Messages.showMessageDialog("Cannot find any stored copy of file " + myFile.getName(),
                                     "Merge",
                                     Messages.getErrorIcon());
          return null;
        }

        if (storedFiles.length == 1) {
          VirtualFile storedCopy = storedFiles[0];
          if (Messages.showYesNoDialog("Use stored copy " + CvsVfsUtil.getPathFor(storedCopy) + " to merge with?",
                                       "Merge", Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE) {
            return null;
          }
          return storedCopy.contentsToByteArray();
        }

        VirtualFile selected = chooseFileFrom(storedFiles);

        if (selected == null) return null;

        return selected.contentsToByteArray();
      }
      else {
        try {
          final GetFileContentOperation operation = GetFileContentOperation
            .createForFile(myFile, new SimpleRevision(getResultRevision()));


          CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
          executor.performActionSync(new CommandCvsHandler("Merge", operation),
                                     new CvsOperationExecutorCallback() {
                                       public void executionFinished(boolean successfully) {
                                       }

                                       public void executeInProgressAfterAction(ModalityContext modaityContext) {
                                       }

                                       public void executionFinishedSuccessfully() {
                                       }
                                     });
          return operation.getFileBytes();
        }
        catch (CannotFindCvsRootException e) {
          throw new IOException(e.getLocalizedMessage());
        }
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }

  }

  protected VirtualFile chooseFileFrom(VirtualFile[] storedFiles) {
    SelectFromListDialog selectFromListDialog = new SelectFromListDialog(myProject, storedFiles, new SelectFromListDialog.ToStringAspect() {
      public String getToStirng(Object obj) {
        return ((VirtualFile)obj).getName();
      }
    },
                                                                         "Choose stored version",
                                                                         ListSelectionModel.SINGLE_SELECTION);
    selectFromListDialog.show();

    if (!selectFromListDialog.isOK()) return null;


    return (VirtualFile)selectFromListDialog.getSelection()[0];

  }


  private MergeData loadRevisionsInternal(final String firstRevision, final String secondRevision) throws VcsException {
    try {
      final GetFileContentOperation fileToMergeWithContentOperation = GetFileContentOperation.
        createForFile(myFile, new SimpleRevision(firstRevision));


      final GetFileContentOperation originalFileContentOperation = GetFileContentOperation.
        createForFile(myFile, new SimpleRevision(secondRevision));

      CompositeOperaton compositeOperaton = new CompositeOperaton();
      compositeOperaton.addOperation(fileToMergeWithContentOperation);
      compositeOperaton.addOperation(originalFileContentOperation);

      CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
      executor.performActionSync(new CommandCvsHandler("Merge", compositeOperaton),
                                 new CvsOperationExecutorCallback() {
                                   public void executionFinished(boolean successfully) {
                                   }

                                   public void executeInProgressAfterAction(ModalityContext modaityContext) {
                                   }

                                   public void executionFinishedSuccessfully() {
                                   }
                                 });
      final MergeData result = new MergeData();
      result.CURRENT = getStoredContent();
      result.ORIGINAL = originalFileContentOperation.getFileBytes();
      result.LAST = fileToMergeWithContentOperation.getFileBytes();
      return result;
    }
    catch (CannotFindCvsRootException e) {
      throw new VcsException(e);
    }
  }



}
