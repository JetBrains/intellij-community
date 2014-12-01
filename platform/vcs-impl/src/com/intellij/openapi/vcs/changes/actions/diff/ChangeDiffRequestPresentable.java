/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.impl.DiffContentFactory;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// TODO: svn integration
public class ChangeDiffRequestPresentable implements DiffRequestPresentable {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestPresentable.class);

  private static Key<List<String>> CONTEXT_KEY = Key.create("Diff.ChangeDiffRequestPresentableContextKey");
  public static Key<ContentRevision[]> CONTENT_REVISIONS = Key.create("DiffRequestPresentable.CONTENT_REVISIONS");

  @NotNull private final Project myProject;
  @NotNull private final Change myChange;
  @NotNull private final Map<Key, Object> myContext;

  public ChangeDiffRequestPresentable(@NotNull Project project, @NotNull Change change) {
    this(project, change, Collections.<Key, Object>emptyMap());
  }

  public ChangeDiffRequestPresentable(@NotNull Project project, @NotNull Change change, @NotNull Map<Key, Object> context) {
    myChange = change;
    myProject = project;
    myContext = context;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  @NotNull
  @Override
  public String getName() {
    return ChangesUtil.getFilePath(myChange).getPath();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException, ProcessCanceledException {
    try {
      return loadCurrentContents(context, indicator);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (DiffRequestPresentableException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.warn(e);
      throw new DiffRequestPresentableException(e.getMessage());
    }
  }

  @NotNull
  private DiffRequest loadCurrentContents(@NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws IOException, VcsException,
                                                                                       DiffRequestPresentableException {
    final ContentRevision bRev = myChange.getBeforeRevision();
    final ContentRevision aRev = myChange.getAfterRevision();

    if (bRev == null && aRev == null) {
      LOG.warn("Both revision contents are empty");
      throw new DiffRequestPresentableException("Bad revisions contents");
    }
    if (bRev != null) checkContentRevision(bRev, context, indicator);
    if (aRev != null) checkContentRevision(aRev, context, indicator);

    // TODO: "MyFile.txt (/some/path/to/)" ?
    String beforePath = getPathPresentable(bRev);
    String afterPath = getPathPresentable(aRev);
    String title;
    if (beforePath != null && afterPath != null && !beforePath.equals(afterPath)) {
      title = beforePath + " -> " + afterPath;
    }
    else if (beforePath != null) {
      title = beforePath;
    }
    else {
      title = afterPath;
    }

    indicator.setIndeterminate(true);
    DiffContent content1 = createContent(bRev, indicator);
    DiffContent content2 = createContent(aRev, indicator);

    String beforeRevisionTitle = (bRev != null) ? bRev.getRevisionNumber().asString() : "";
    String afterRevisionTitle = (aRev != null) ? aRev.getRevisionNumber().asString() : "";
    if ((beforeRevisionTitle == null) || (beforeRevisionTitle.length() == 0)) {
      beforeRevisionTitle = "Base version";
    }
    if ((afterRevisionTitle == null) || (afterRevisionTitle.length() == 0)) {
      afterRevisionTitle = "Your version";
    }

    SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);
    request.putUserData(CONTENT_REVISIONS, new ContentRevision[]{aRev, bRev});

    for (Map.Entry<Key, Object> entry : myContext.entrySet()) {
      request.putUserData(entry.getKey(), entry.getValue());
    }

    return request;
  }

  @Nullable
  private static String getPathPresentable(@Nullable ContentRevision revision) {
    return revision != null ? FileUtil.toSystemDependentName(revision.getFile().getPath()) : null;
  }

  @NotNull
  private DiffContent createContent(@Nullable ContentRevision revision,
                                    @NotNull ProgressIndicator indicator) throws IOException, VcsException,
                                                                                 DiffRequestPresentableException {
    indicator.checkCanceled();

    if (revision == null) return DiffContentFactory.createEmpty();

    if (revision instanceof CurrentContentRevision) {
      VirtualFile vFile = ((CurrentContentRevision)revision).getVirtualFile();
      if (vFile == null) throw new DiffRequestPresentableException("Can't get current revision content");
      return DiffContentFactory.create(myProject, vFile);
    }

    FilePath filePath = revision.getFile();
    if (revision instanceof BinaryContentRevision) {
      byte[] content = ((BinaryContentRevision)revision).getBinaryContent();
      if (content == null) {
        throw new DiffRequestPresentableException("Can't get binary revision content");
      }
      return DiffContentFactory.createBinary(myProject, filePath.getName(), filePath.getPath(), content);
    }

    String revisionContent = revision.getContent();
    if (revisionContent == null) throw new IOException("Can't get revision content");
    return FileAwareDocumentContent.create(myProject, revisionContent, filePath);
  }

  private void checkContentRevision(@NotNull ContentRevision rev,
                                    @NotNull UserDataHolder context,
                                    @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    if (rev.getFile().isDirectory()) {
      throw new DiffRequestPresentableException("Can't show diff for directory");
    }

    final FileType type = rev.getFile().getFileType();
    if (!type.isBinary()) return;

    if (FileTypes.UNKNOWN.equals(type)) {
      checkAssociate(myProject, rev.getFile(), context, indicator);
    }
  }

  private static void checkAssociate(@NotNull final Project project,
                                     @NotNull final FilePath file,
                                     @NotNull final UserDataHolder context,
                                     @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    final String pattern = FileUtilRt.getExtension(file.getName()).toLowerCase();
    if (getSkippedExtensionsFromContext(context).contains(pattern)) {
      throw new DiffRequestPresentableException("Unknown file type");
    }

    final boolean[] ret = new boolean[1];
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int result = Messages.showOkCancelDialog(project,
                                                 VcsBundle.message("diff.unknown.file.type.prompt", file.getName()),
                                                 VcsBundle.message("diff.unknown.file.type.title"),
                                                 VcsBundle.message("diff.unknown.file.type.associate"),
                                                 CommonBundle.getCancelButtonText(),
                                                 Messages.getQuestionIcon());
        if (result == Messages.OK) {
          FileType fileType = FileTypeChooser.associateFileType(file.getName());
          ret[0] = fileType != null && !FileTypes.UNKNOWN.equals(fileType);
        }
        else {
          getSkippedExtensionsFromContext(context).add(pattern);
          ret[0] = false;
        }
      }
    }, indicator.getModalityState());

    if (!ret[0]) throw new DiffRequestPresentableException("Unknown file type");
  }

  @NotNull
  private static List<String> getSkippedExtensionsFromContext(@NotNull UserDataHolder context) {
    List<String> strings = CONTEXT_KEY.get(context);
    if (strings == null) {
      strings = new ArrayList<String>();
      context.putUserData(CONTEXT_KEY, strings);
    }
    return strings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeDiffRequestPresentable that = (ChangeDiffRequestPresentable)o;

    return myChange.equals(that.myChange);
  }

  @Override
  public int hashCode() {
    return myChange.hashCode();
  }
}
