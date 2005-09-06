package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.history.ComparableVcsRevisionOnOperation;
import com.intellij.cvsSupport2.history.CvsFileContent;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class CvsDiffProvider implements DiffProvider{
  private final Project myProject;

  public CvsDiffProvider(final Project project) {
    myProject = project;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    return new CvsRevisionNumber(CvsEntriesManager.getInstance().getEntryFor(file).getRevision());
  }

  public VcsRevisionNumber getLastRevision(VirtualFile virtualFile) {
    final String stickyData = CvsUtil.getStickyDateForDirectory(virtualFile.getParent());
    if (stickyData != null) {
      return new CvsRevisionNumber(stickyData);
    } else {
      return new CvsRevisionNumber("HEAD");      
    }
  }

  public VcsFileContent createFileContent(final VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    final GetFileContentOperation operation = new GetFileContentOperation(new File(CvsUtil.getModuleName(selectedFile)),
                                                                          CvsEntriesManager.getInstance()
                                                                            .getCvsConnectionSettingsFor(selectedFile.getParent()),
                                                                          new SimpleRevision(revisionNumber.asString())
    );
    return new CvsFileContent(new ComparableVcsRevisionOnOperation(operation, myProject)) {
      public VcsRevisionNumber getRevisionNumber() {
        return revisionNumber;
      }
    };
  }
}
