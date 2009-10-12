/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.changeBrowser.CvsBinaryContentRevision;
import com.intellij.cvsSupport2.changeBrowser.CvsContentRevision;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsExecution.ModalityContextImpl;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsStatus.StatusOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;
import org.netbeans.lib.cvsclient.file.FileStatus;

import java.io.File;
import java.util.Collections;
import java.util.List;

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
    // shouldn't use sticky date: we are to get latest revision that exists _in repository_
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    final List<File> files = Collections.singletonList(file);
    final StatusOperation statusOperation = new StatusOperation(files) {
      @Override
      protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
        final StatusCommand command = (StatusCommand) super.createCommand(root, cvsExecutionEnvironment);
        command.setIncludeTags(true);
        return command;
      }
    };
    final Ref<Boolean> success = new Ref<Boolean>();
    final CvsOperationExecutorCallback callback = new CvsOperationExecutorCallback() {
      public void executionFinished(final boolean successfully) {
      }

      public void executionFinishedSuccessfully() {
        success.set(Boolean.TRUE);
      }

      public void executeInProgressAfterAction(final ModalityContext modaityContext) {
      }
    };
    final CommandCvsHandler cvsHandler = new CommandCvsHandler(CvsBundle.message("operation.name.get.file.status"), statusOperation) {
      @Override
      protected boolean runInReadThread() {
        return false;
      }
    };
    executor.performActionSync(cvsHandler, callback);

    if (Boolean.TRUE.equals(success.get())) {
      if ((statusOperation.getStickyDate() != null) || (statusOperation.getStickyTag() != null)) {
        final String headRevision = getHeadRevisionFromLog(statusOperation.getRepositoryRevision(), file);
        if (headRevision != null) {
          return new ItemLatestState(new CvsRevisionNumber(headRevision),
                                 (statusOperation.getStatus() != null) && (! FileStatus.REMOVED.equals(statusOperation.getStatus())));
        }
      } else {
        return new ItemLatestState(new CvsRevisionNumber(statusOperation.getRepositoryRevision()),
                                 (statusOperation.getStatus() != null) && (! FileStatus.REMOVED.equals(statusOperation.getStatus())));
      }
    }

    return new ItemLatestState(new CvsRevisionNumber("HEAD"), true);
  }

  @Nullable
  private static String getHeadRevisionFromLog(final String latestKnownRevision, final File file) {
    final LogOperation operation = new LogOperation(Collections.<FilePath>singletonList(new FilePathImpl(file, false))) {
      @Override
      protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
        final LogCommand command = (LogCommand) super.createCommand(root, cvsExecutionEnvironment);
        command.setRevisionFilter(latestKnownRevision + ":");
        return command;
      }
    };
    final Ref<Boolean> logSuccess = new Ref<Boolean>(Boolean.TRUE);
    final ModalityContext context = ModalityContextImpl.NON_MODAL;
    final CvsExecutionEnvironment cvsExecutionEnvironment = new CvsExecutionEnvironment(new CvsMessagesAdapter(),
      CvsExecutionEnvironment.DUMMY_STOPPER, new ErrorProcessor() {
      public void addError(VcsException ex) {
        logSuccess.set(Boolean.FALSE);
      }
      public void addWarning(VcsException ex) {
      }
      public List getErrors() {
        return null;
      }
    }, context, PostCvsActivity.DEAF);
    try {
      // should already be logged in
      //operation.login(context);
      operation.execute(cvsExecutionEnvironment);
    }
    catch (VcsException e) {
      //
    }
    catch (CommandAbortedException e) {
      //
    }
    if (Boolean.TRUE.equals(logSuccess.get())) {
      final List<LogInformation> informations = operation.getLogInformationList();
      if (informations != null && (! informations.isEmpty())) {
        return informations.get(0).getHeadRevision();
      }
    }
    return null;
  }
}
