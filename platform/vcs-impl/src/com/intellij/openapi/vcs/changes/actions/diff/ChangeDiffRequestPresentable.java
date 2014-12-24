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
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.GenericDataProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.impl.DiffContentFactory;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChangeDiffRequestPresentable implements DiffRequestPresentable {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestPresentable.class);

  private static Key<List<String>> CONTEXT_KEY = Key.create("Diff.ChangeDiffRequestPresentableContextKey");
  public static Key<ContentRevision[]> CONTENT_REVISIONS = Key.create("DiffRequestPresentable.CONTENT_REVISIONS");

  @NotNull private final Project myProject;
  @NotNull private final Change myChange;
  @NotNull private final Map<Key, Object> myChangeContext;

  @Nullable
  public static ChangeDiffRequestPresentable create(@NotNull Project project,
                                                    @NotNull Change change,
                                                    @NotNull Map<Key, Object> changeContext) {
    if (!canCreate(project, change)) return null;
    return new ChangeDiffRequestPresentable(project, change, changeContext);
  }

  private static boolean canCreate(@NotNull Project project, @NotNull Change change) {
    for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensions()) {
      if (provider.canCreate(project, change)) return true;
    }

    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();

    if (bRev == null && aRev == null) return false;
    if (bRev != null && bRev.getFile().isDirectory()) return false;
    if (aRev != null && aRev.getFile().isDirectory()) return false;

    return true;
  }

  private ChangeDiffRequestPresentable(@NotNull Project project, @NotNull Change change, @NotNull Map<Key, Object> changeContext) {
    myChange = change;
    myProject = project;
    myChangeContext = changeContext;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  @NotNull
  public Project getProject() {
    return myProject;
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
  protected DiffRequest loadCurrentContents(@NotNull UserDataHolder context,
                                            @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    DiffRequest request = null;
    for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensions()) {
      if (provider.canCreate(myProject, myChange)) {
        request = provider.process(this, context, indicator);
        break;
      }
    }
    if (request == null) request = createRequest(myProject, myChange, context, indicator);

    request.putUserData(CONTENT_REVISIONS, new ContentRevision[]{myChange.getBeforeRevision(), myChange.getAfterRevision()});

    for (Map.Entry<Key, Object> entry : myChangeContext.entrySet()) {
      request.putUserData(entry.getKey(), entry.getValue());
    }

    DataProvider dataProvider = request.getUserData(DiffUserDataKeys.DATA_PROVIDER);
    if (dataProvider == null) {
      dataProvider = new GenericDataProvider();
      request.putUserData(DiffUserDataKeys.DATA_PROVIDER, dataProvider);
    }
    if (dataProvider instanceof GenericDataProvider) ((GenericDataProvider)dataProvider).putData(VcsDataKeys.CURRENT_CHANGE, myChange);

    return request;
  }

  @NotNull
  public static DiffRequest createRequest(@NotNull Project project,
                                          @NotNull Change change,
                                          @NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    if (ChangesUtil.isTextConflictingChange(change)) { // three side diff
      // FIXME: This part is ugly as a VCS merge subsystem itself.

      FilePath path = ChangesUtil.getFilePath(change);
      VirtualFile file = path.getVirtualFile();
      if (file == null) {
        path.hardRefresh();
        file = path.getVirtualFile();
      }
      if (file == null) throw new DiffRequestPresentableException("Can't show merge conflict - file not found");

      final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
      if (vcs == null || vcs.getMergeProvider() == null) {
        throw new DiffRequestPresentableException("Can't show merge conflict - operation nos supported");
      }
      try {
        // FIXME: loadRevisions() can call runProcessWithProgressSynchronously() inside
        final Ref<Throwable> exceptionRef = new Ref<Throwable>();
        final Ref<MergeData> mergeDataRef = new Ref<MergeData>();
        final VirtualFile finalFile = file;
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            try {
              mergeDataRef.set(vcs.getMergeProvider().loadRevisions(finalFile));
            }
            catch (VcsException e) {
              exceptionRef.set(e);
            }
          }
        });
        if (!exceptionRef.isNull()) {
          Throwable e = exceptionRef.get();
          if (e instanceof VcsException) throw (VcsException)e;
          if (e instanceof Error) throw (Error)e;
          if (e instanceof RuntimeException) throw (RuntimeException)e;
          throw new RuntimeException(e);
        }
        MergeData mergeData = mergeDataRef.get();

        ContentRevision bRev = change.getBeforeRevision();
        ContentRevision aRev = change.getAfterRevision();
        String beforeRevisionTitle = getRevisionTitle(bRev, "Your version");
        String afterRevisionTitle = getRevisionTitle(aRev, "Server version");

        String title = FileUtil.toSystemDependentName(file.getPresentableUrl());
        String[] titles = new String[]{beforeRevisionTitle, "Base Version", afterRevisionTitle};

        // Yep, we hope that it's a text file. And that charset wasn't changed.
        DiffContent[] contents = new DiffContent[]{
          createTextContent(mergeData.CURRENT, file),
          createTextContent(mergeData.ORIGINAL, file),
          createTextContent(mergeData.LAST, file)
        };

        return new SimpleDiffRequest(title, contents, titles);
      }
      catch (VcsException e) {
        LOG.info(e);
        throw new DiffRequestPresentableException(e);
      }
    }
    else {
      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();

      if (bRev == null && aRev == null) {
        LOG.warn("Both revision contents are empty");
        throw new DiffRequestPresentableException("Bad revisions contents");
      }
      if (bRev != null) checkContentRevision(project, bRev, context, indicator);
      if (aRev != null) checkContentRevision(project, aRev, context, indicator);

      String title = getRequestTitle(bRev, aRev);

      indicator.setIndeterminate(true);
      DiffContent content1 = createContent(project, bRev, context, indicator);
      DiffContent content2 = createContent(project, aRev, context, indicator);

      String beforeRevisionTitle = getRevisionTitle(bRev, "Base version");
      String afterRevisionTitle = getRevisionTitle(aRev, "Your version");

      return new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);
    }
  }

  @NotNull
  public static String getRequestTitle(@Nullable ContentRevision bRev, @Nullable ContentRevision aRev) {
    assert bRev != null || aRev != null;
    if (bRev != null && aRev != null) {
      FilePath bPath = bRev.getFile();
      FilePath aPath = aRev.getFile();
      if (bPath.equals(aPath)) {
        return getPathPresentable(bPath);
      }
      else {
        return getPathPresentable(bPath, aPath);
      }
    }
    else if (bRev != null) {
      return getPathPresentable(bRev.getFile());
    }
    else {
      return getPathPresentable(aRev.getFile());
    }
  }

  @NotNull
  public static String getPathPresentable(@NotNull FilePath path) {
    FilePath parentPath = path.getParentPath();
    if (!path.isDirectory() && parentPath != null) {
      return path.getName() + " (" + parentPath.getPath() + ")";
    }
    else {
      return path.getPath();
    }
  }

  @NotNull
  public static String getPathPresentable(@NotNull FilePath bPath, @NotNull FilePath aPath) {
    FilePath bParentPath = bPath.getParentPath();
    FilePath aParentPath = aPath.getParentPath();
    if (Comparing.equal(bParentPath, aParentPath)) {
      if (bParentPath != null) {
        return bPath.getName() + " -> " + aPath.getName() + " (" + bParentPath.getPath() + ")";
      }
      else {
        return bPath.getPath() + " -> " + aPath.getPath();
      }
    }
    else {
      if (bPath.getName().equals(aPath.getName())) {
        if (bParentPath != null && aParentPath != null) {
          return bPath.getName() + " (" + bParentPath.getPath() + " -> " + bParentPath.getPath() + ")";
        }
        else {
          return bPath.getPath() + " -> " + aPath.getPath();
        }
      }
      else {
        if (bParentPath != null && aParentPath != null) {
          return bPath.getName() + " -> " + aPath.getName() + " (" + bParentPath.getPath() + " -> " + bParentPath.getPath() + ")";
        }
        else {
          return bPath.getPath() + " -> " + aPath.getPath();
        }
      }
    }
  }

  @NotNull
  public static String getRevisionTitle(@Nullable ContentRevision revision, @NotNull String defaultValue) {
    if (revision == null) return defaultValue;
    String title = revision.getRevisionNumber().asString();
    if (title == null || title.isEmpty()) return defaultValue;
    return title;
  }

  @NotNull
  public static DiffContent createContent(@NotNull Project project,
                                          @Nullable ContentRevision revision,
                                          @NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    try {
      indicator.checkCanceled();

      if (revision == null) return DiffContentFactory.createEmpty();

      if (revision instanceof CurrentContentRevision) {
        VirtualFile vFile = ((CurrentContentRevision)revision).getVirtualFile();
        if (vFile == null) throw new DiffRequestPresentableException("Can't get current revision content");
        return DiffContentFactory.create(project, vFile);
      }

      FilePath filePath = revision.getFile();
      if (revision instanceof BinaryContentRevision) {
        if (FileTypes.UNKNOWN.equals(filePath.getFileType())) {
          checkAssociate(project, filePath, context, indicator);
        }

        byte[] content = ((BinaryContentRevision)revision).getBinaryContent();
        if (content == null) {
          throw new DiffRequestPresentableException("Can't get binary revision content");
        }
        return DiffContentFactory.createBinary(project, filePath.getName(), filePath.getFileType(), content);
      }

      String revisionContent = revision.getContent();
      if (revisionContent == null) throw new DiffRequestPresentableException("Can't get revision content");
      return FileAwareDocumentContent.create(project, revisionContent, filePath);
    }
    catch (IOException e) {
      LOG.info(e);
      throw new DiffRequestPresentableException(e);
    }
    catch (VcsException e) {
      LOG.info(e);
      throw new DiffRequestPresentableException(e);
    }
  }

  @NotNull
  public static DiffContent createTextContent(@NotNull byte[] bytes, @NotNull VirtualFile file) {
    return DiffContentFactory.create(CharsetToolkit.bytesToString(bytes, file.getCharset()), file.getFileType());
  }

  public static void checkContentRevision(@NotNull Project project,
                                          @NotNull ContentRevision rev,
                                          @NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException {
    if (rev.getFile().isDirectory()) {
      throw new DiffRequestPresentableException("Can't show diff for directory");
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
