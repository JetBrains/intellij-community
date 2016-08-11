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
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
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
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CvsAnnotationProvider implements AnnotationProvider{

  @NonNls private static final String INVALID_OPTION_F = "invalid option -- F";
  @NonNls private static final String USAGE_CVSNTSRV_SERVER = "Usage: cvs";
  private static final Collection<String> ourDoNotAnnotateBinaryRoots = new HashSet<>();

  private final Project myProject;
  private final CvsHistoryProvider myCvsHistoryProvider;

  public CvsAnnotationProvider(final Project project, CvsHistoryProvider cvsHistoryProvider) {
    myProject = project;
    myCvsHistoryProvider = cvsHistoryProvider;
  }

  public FileAnnotation annotate(VirtualFile virtualFile) throws VcsException {
    final File file = new File(virtualFile.getPath());
    final File cvsLightweightFile = CvsUtil.getCvsLightweightFileForFile(file);
    final String revision = CvsUtil.getRevisionFor(file);
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    final CvsConnectionSettings root = entriesManager.getCvsConnectionSettingsFor(file.getParentFile());
    final boolean binary = annotateBinary(virtualFile, root);
    final AnnotateOperation operation = executeOperation(cvsLightweightFile, revision, root, binary, true);

    final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(virtualFile);
    final List<VcsFileRevision> revisions = myCvsHistoryProvider.createRevisions(filePath);
    final Annotation[] lineAnnotations = operation.getLineAnnotations();
    adjustAnnotation(revisions, lineAnnotations);
    return new CvsFileAnnotation(operation.getContent(), lineAnnotations, revisions, virtualFile, revision, myProject);
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
    final boolean binary = annotateBinary(cvsVirtualFile, environment);
    final AnnotateOperation annotateOperation = executeOperation(cvsFile, revision, environment, binary, true);
    final Annotation[] lineAnnotations = annotateOperation.getLineAnnotations();
    final List<VcsFileRevision> revisions;
    if (hasLocalFile) {
      final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(cvsVirtualFile);
      revisions = myCvsHistoryProvider.createRevisions(filePath);
      // in annotation cvs returns only 8 symbols of username
      // try to find usernames in history and use them
      adjustAnnotation(revisions, lineAnnotations);
    }
    else {
      // imitation
      revisions = new ArrayList<>();
      final Set<String> usedRevisions = new HashSet<>();
      for (Annotation annotation : lineAnnotations) {
        if (! usedRevisions.contains(annotation.getRevision())) {
          revisions.add(new RevisionPresentation(annotation.getRevision(), annotation.getUserName(), annotation.getDate()));
          usedRevisions.add(annotation.getRevision());
        }
      }
    }
    return new CvsFileAnnotation(annotateOperation.getContent(), lineAnnotations, revisions, cvsVirtualFile, revision, myProject);
  }

  private static boolean annotateBinary(VirtualFile cvsVirtualFile, CvsEnvironment environment) {
    if (ourDoNotAnnotateBinaryRoots.contains(environment.getCvsRootAsString())) {
      return false;
    }
    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(cvsVirtualFile);
    if (entry != null) {
      return entry.isBinary();
    }
    return cvsVirtualFile.getFileType().isBinary();
  }

  private AnnotateOperation executeOperation(File file, String revision, CvsEnvironment root, boolean binary, boolean retryOnFailure)
    throws VcsException {
    final AnnotateOperation operation = new AnnotateOperation(file, revision, root, binary);
    final CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), operation), CvsOperationExecutorCallback.EMPTY);
    final CvsResult result = executor.getResult();
    if (result.hasErrors()) {
      if (!retryOnFailure) {
        throw result.composeError();
      }
      for (VcsException error : result.getErrors()) {
        for (String message : error.getMessages()) {
          if (message.contains(INVALID_OPTION_F) || message.contains(USAGE_CVSNTSRV_SERVER)) {
            ourDoNotAnnotateBinaryRoots.add(root.getCvsRootAsString());
            return executeOperation(file, revision, root, false, false);
          }
        }
      }
      throw result.composeError();
    }
    return operation;
  }

  private static void adjustAnnotation(@Nullable List<VcsFileRevision> revisions, @NotNull Annotation[] lineAnnotations) {
    if (revisions != null) {
      final Map<String, VcsFileRevision> revisionMap = new HashMap<>();
      for (VcsFileRevision vcsFileRevision : revisions) {
        revisionMap.put(vcsFileRevision.getRevisionNumber().asString(), vcsFileRevision);
      }
      for (Annotation lineAnnotation : lineAnnotations) {
        final String revisionNumber = lineAnnotation.getRevision();
        final VcsFileRevision revision = revisionMap.get(revisionNumber);
        if (revision != null) {
          lineAnnotation.setUser(revision.getAuthor());
          lineAnnotation.setDate(revision.getRevisionDate());
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

    @Nullable
    @Override
    public RepositoryLocation getChangedRepositoryPath() {
      return null;
    }

    public byte[] loadContent() throws IOException, VcsException {
      return getContent();
    }

    public byte[] getContent() throws IOException, VcsException {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

}
