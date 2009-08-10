package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.changeBrowser.CvsBinaryContentRevision;
import com.intellij.cvsSupport2.changeBrowser.CvsContentRevision;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsStatus.StatusOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.FileStatus;

import java.io.File;
import java.util.Collections;

public class CvsDiffProvider implements DiffProvider{
  private final Project myProject;

  public CvsDiffProvider(final Project project) {
    myProject = project;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(file);
    if (entry == null) return null;
    return new CvsRevisionNumber(entry.getRevision());
  }

  public ItemLatestState getLastRevision(VirtualFile virtualFile) {
    return getLastRevision(CvsVfsUtil.getFileFor(virtualFile));
  }

  public ContentRevision createFileContent(final VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    if ((revisionNumber instanceof CvsRevisionNumber)) {
      final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(selectedFile.getParent());
      final File file = new File(CvsUtil.getModuleName(selectedFile));
      final CvsRevisionNumber cvsRevisionNumber = ((CvsRevisionNumber)revisionNumber);
      final RevisionOrDate versionInfo;
      if (cvsRevisionNumber.getDateOrRevision() != null) {
        versionInfo = RevisionOrDateImpl.createOn(cvsRevisionNumber.getDateOrRevision());
      }
      else {
        versionInfo = new SimpleRevision(cvsRevisionNumber.asString());
      }

      if (selectedFile.getFileType().isBinary()) {
        return new CvsBinaryContentRevision(file, file, versionInfo, settings, myProject);
      }
      else {
        return new CvsContentRevision(file, file, versionInfo, settings, myProject);
      }

    } else {
      return null;
    }
  }

  public ItemLatestState getLastRevision(FilePath filePath) {
    return getLastRevision(filePath.getIOFile());
  }

  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  private ItemLatestState getLastRevision(final File file) {
    final String stickyData = CvsUtil.getStickyDateForDirectory(file.getParentFile());

    if (stickyData != null) {
      return new ItemLatestState(new CvsRevisionNumber(stickyData), true);
    } else {
      CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
      final StatusOperation statusOperation = new StatusOperation(Collections.singletonList(file));
      final Ref<Boolean> success = new Ref<Boolean>();
      executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.get.file.status"), statusOperation),
      new CvsOperationExecutorCallback() {
        public void executionFinished(final boolean successfully) {
        }
        public void executionFinishedSuccessfully() {
          success.set(Boolean.TRUE);
        }
        public void executeInProgressAfterAction(final ModalityContext modaityContext) {
        }
      });

      if (Boolean.TRUE.equals(success.get())) {
        return new ItemLatestState(new CvsRevisionNumber(statusOperation.getRepositoryRevision()),
                                   (statusOperation.getStatus() != null) && (! FileStatus.REMOVED.equals(statusOperation.getStatus())));
      }

      return new ItemLatestState(new CvsRevisionNumber("HEAD"), true);
    }
  }
}
