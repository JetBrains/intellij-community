/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class CvsAnnotationProvider implements AnnotationProvider{
  private final Project myProject;

  public CvsAnnotationProvider(final Project project) {
    myProject = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    final AnnotateOperation operation = AnnotateOperation.createForFile(new File(file.getPath()));
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), operation),
                               CvsOperationExecutorCallback.EMPTY);
    if (executor.getResult().hasNoErrors()) {
      final CvsVcs2 cvsVcs2 = CvsVcs2.getInstance(myProject);
      final CvsHistoryProvider historyProvider = (CvsHistoryProvider) cvsVcs2.getVcsHistoryProvider();
      final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
      final List<VcsFileRevision> revisions = historyProvider.createRevisions(filePath);
      final Annotation[] lineAnnotations = operation.getLineAnnotations();
      adjustAnnotation(revisions, lineAnnotations);
      return new CvsFileAnnotation(operation.getContent(), lineAnnotations, revisions, file);
    } else {
      throw executor.getFirstError();
    }
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParent());
    return annotate(file, revision.getRevisionNumber().asString(), settings);
  }

  public boolean isAnnotationValid(VcsFileRevision rev){
    return true;
  }

  public FileAnnotation annotate(VirtualFile cvsVirtualFile, String revision, CvsEnvironment environment) throws VcsException {
    // the VirtualFile has a full path if annotate is called from history (when we have a real file on disk),
    // and has the path equal to a CVS module name if annotate is called from the CVS repository browser
    // (when there's no real path)
    boolean hasLocalFile = false;
    File cvsFile = new File(cvsVirtualFile.getPath());
    if (cvsFile.isAbsolute()) {
      hasLocalFile = true;
      cvsFile = new File(CvsUtil.getModuleName(cvsVirtualFile));
    }
    final AnnotateOperation annotateOperation = new AnnotateOperation(cvsFile, revision, environment);
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), annotateOperation),
                               CvsOperationExecutorCallback.EMPTY);

    if (executor.getResult().hasNoErrors()) {
      final List<VcsFileRevision> revisions;
      final Annotation[] lineAnnotations = annotateOperation.getLineAnnotations();
      if (hasLocalFile) {
        final CvsVcs2 cvsVcs2 = CvsVcs2.getInstance(myProject);
        final CvsHistoryProvider historyProvider = (CvsHistoryProvider) cvsVcs2.getVcsHistoryProvider();
        final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(cvsVirtualFile);
        revisions = historyProvider.createRevisions(filePath);
        // in annotation cvs returns only 8 symbols of username
        // try to find usernames in history and use them
        adjustAnnotation(revisions, lineAnnotations);
      }
      else {
        // imitation
        revisions = new ArrayList<VcsFileRevision>();
        final Set<String> usedRevisions = new HashSet<String>();
        for (Annotation annotation : lineAnnotations) {
          if (! usedRevisions.contains(annotation.getRevision())) {
            revisions.add(new RevisionPresentation(annotation.getRevision(), annotation.getUserName(), annotation.getDate()));
            usedRevisions.add(annotation.getRevision());
          }
        }
      }
      return new CvsFileAnnotation(annotateOperation.getContent(), lineAnnotations, revisions, cvsVirtualFile);
    }
    else {
      throw executor.getResult().composeError();
    }

  }

  private static void adjustAnnotation(@Nullable List<VcsFileRevision> revisions, @NotNull Annotation[] lineAnnotations) {
    if (revisions != null) {
      final Map<VcsRevisionNumber, String> usersMap = new HashMap<VcsRevisionNumber, String>();
      for (VcsFileRevision vcsFileRevision : revisions) {
        usersMap.put(vcsFileRevision.getRevisionNumber(), vcsFileRevision.getAuthor());
      }
      for (Annotation lineAnnotation : lineAnnotations) {
        final String name = usersMap.get(new CvsRevisionNumber(lineAnnotation.getRevision()));
        if (name != null) {
          lineAnnotation.setUser(name);
        }
      }
    }
  }

  private static class RevisionPresentation implements VcsFileRevision {
    private final VcsRevisionNumber myNumber;
    private final String myAuthor;
    private final Date myDate;

    private RevisionPresentation(final String revision, final String author, final Date date) {
      myNumber = new CvsRevisionNumber(revision);
      myAuthor = author;
      myDate = date;
    }

    public VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }

    public String getBranchName() {
      return null;
    }

    public Date getRevisionDate() {
      return myDate;
    }

    public String getAuthor() {
      return myAuthor;
    }

    public String getCommitMessage() {
      return null;
    }

    public void loadContent() throws VcsException {
    }

    public byte[] getContent() {
      return new byte[0];
    }
  }

}
