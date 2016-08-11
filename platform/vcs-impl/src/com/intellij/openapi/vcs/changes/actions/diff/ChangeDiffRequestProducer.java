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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileAwareDocumentContent;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChangeDiffRequestProducer implements DiffRequestProducer {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestProducer.class);

  public static Key<Change> CHANGE_KEY = Key.create("DiffRequestPresentable.Change");

  @Nullable private final Project myProject;
  @NotNull private final Change myChange;
  @NotNull private final Map<Key, Object> myChangeContext;

  private ChangeDiffRequestProducer(@Nullable Project project, @NotNull Change change, @NotNull Map<Key, Object> changeContext) {
    myChange = change;
    myProject = project;
    myChangeContext = changeContext;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public String getName() {
    return ChangesUtil.getFilePath(myChange).getPath();
  }

  public static boolean isEquals(@NotNull Change change1, @NotNull Change change2) {
    for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensions()) {
      ThreeState equals = provider.isEquals(change1, change2);
      if (equals == ThreeState.NO) return false;
    }
    for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensions()) {
      ThreeState equals = provider.isEquals(change1, change2);
      if (equals == ThreeState.YES) return true;
      if (equals == ThreeState.NO) return false;
    }

    if (!Comparing.equal(change1.getClass(), change2.getClass())) return false;
    if (!isEquals(change1.getBeforeRevision(), change2.getBeforeRevision())) return false;
    if (!isEquals(change1.getAfterRevision(), change2.getAfterRevision())) return false;

    return true;
  }

  private static boolean isEquals(@Nullable ContentRevision revision1, @Nullable ContentRevision revision2) {
    if (Comparing.equal(revision1, revision2)) return true;
    if (revision1 instanceof CurrentContentRevision && revision2 instanceof CurrentContentRevision) {
      VirtualFile vFile1 = ((CurrentContentRevision)revision1).getVirtualFile();
      VirtualFile vFile2 = ((CurrentContentRevision)revision2).getVirtualFile();
      return Comparing.equal(vFile1, vFile2);
    }
    return false;
  }

  @Nullable
  public static ChangeDiffRequestProducer create(@Nullable Project project, @NotNull Change change) {
    return create(project, change, Collections.<Key, Object>emptyMap());
  }

  @Nullable
  public static ChangeDiffRequestProducer create(@Nullable Project project,
                                                 @NotNull Change change,
                                                 @NotNull Map<Key, Object> changeContext) {
    if (!canCreate(project, change)) return null;
    return new ChangeDiffRequestProducer(project, change, changeContext);
  }

  public static boolean canCreate(@Nullable Project project, @NotNull Change change) {
    for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensions()) {
      if (provider.canCreate(project, change)) return true;
    }
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

  @NotNull
  @Override
  public DiffRequest process(@NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    try {
      return loadCurrentContents(context, indicator);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (DiffRequestProducerException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
      throw new DiffRequestProducerException(e.getMessage());
    }
  }

  @NotNull
  protected DiffRequest loadCurrentContents(@NotNull UserDataHolder context,
                                            @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
    DiffRequestProducerException wrapperException = null;
    DiffRequestProducerException requestException = null;

    DiffViewerWrapper wrapper = null;
    try {
      for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensions()) {
        if (provider.canCreate(myProject, myChange)) {
          wrapper = provider.process(this, context, indicator);
          break;
        }
      }
    }
    catch (DiffRequestProducerException e) {
      wrapperException = e;
    }

    DiffRequest request = null;
    try {
      for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensions()) {
        if (provider.canCreate(myProject, myChange)) {
          request = provider.process(this, context, indicator);
          break;
        }
      }
      if (request == null) request = createRequest(myProject, myChange, context, indicator);
    }
    catch (DiffRequestProducerException e) {
      requestException = e;
    }

    if (requestException != null && wrapperException != null) {
      String message = requestException.getMessage() + "\n\n" + wrapperException.getMessage();
      throw new DiffRequestProducerException(message);
    }
    if (requestException != null) {
      request = new ErrorDiffRequest(getRequestTitle(myChange), requestException);
      LOG.info("Request: " + requestException.getMessage());
    }
    if (wrapperException != null) {
      LOG.info("Wrapper: " + wrapperException.getMessage());
    }

    request.putUserData(CHANGE_KEY, myChange);
    request.putUserData(DiffViewerWrapper.KEY, wrapper);

    for (Map.Entry<Key, Object> entry : myChangeContext.entrySet()) {
      request.putUserData(entry.getKey(), entry.getValue());
    }

    DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_CHANGE, myChange);

    return request;
  }

  @NotNull
  private DiffRequest createRequest(@Nullable Project project,
                                           @NotNull Change change,
                                           @NotNull UserDataHolder context,
                                           @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
    if (ChangesUtil.isTextConflictingChange(change)) { // three side diff
      // FIXME: This part is ugly as a VCS merge subsystem itself.

      FilePath path = ChangesUtil.getFilePath(change);
      VirtualFile file = path.getVirtualFile();
      if (file == null) {
        file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
      }
      if (file == null) throw new DiffRequestProducerException("Can't show merge conflict - file not found");

      if (project == null) {
        throw new DiffRequestProducerException("Can't show merge conflict - project is unknown");
      }
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
      if (vcs == null || vcs.getMergeProvider() == null) {
        throw new DiffRequestProducerException("Can't show merge conflict - operation nos supported");
      }
      try {
        // FIXME: loadRevisions() can call runProcessWithProgressSynchronously() inside
        final Ref<Throwable> exceptionRef = new Ref<>();
        final Ref<MergeData> mergeDataRef = new Ref<>();
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

        String title = DiffRequestFactory.getInstance().getTitle(file);
        List<String> titles = ContainerUtil.list(beforeRevisionTitle, "Base Version", afterRevisionTitle);

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        List<DiffContent> contents = ContainerUtil.list(
          contentFactory.createFromBytes(project, file, mergeData.CURRENT),
          contentFactory.createFromBytes(project, file, mergeData.ORIGINAL),
          contentFactory.createFromBytes(project, file, mergeData.LAST)
        );

        SimpleDiffRequest request = new SimpleDiffRequest(title, contents, titles);
        MergeUtil.putRevisionInfos(request, mergeData);

        return request;
      }
      catch (VcsException e) {
        LOG.info(e);
        throw new DiffRequestProducerException(e);
      }
      catch (IOException e) {
        LOG.info(e);
        throw new DiffRequestProducerException(e);
      }
    }
    else {
      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();

      if (bRev == null && aRev == null) {
        LOG.warn("Both revision contents are empty");
        throw new DiffRequestProducerException("Bad revisions contents");
      }
      if (bRev != null) checkContentRevision(project, bRev, context, indicator);
      if (aRev != null) checkContentRevision(project, aRev, context, indicator);

      String title = getRequestTitle(change);

      indicator.setIndeterminate(true);
      DiffContent content1 = createContent(project, bRev, context, indicator);
      DiffContent content2 = createContent(project, aRev, context, indicator);

      final String userLeftRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE);
      String beforeRevisionTitle = userLeftRevisionTitle != null ? userLeftRevisionTitle : getRevisionTitle(bRev, "Base version");
      final String userRightRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE);
      String afterRevisionTitle = userRightRevisionTitle != null ? userRightRevisionTitle : getRevisionTitle(aRev, "Your version");

      SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);

      boolean bRevCurrent = bRev instanceof CurrentContentRevision;
      boolean aRevCurrent = aRev instanceof CurrentContentRevision;
      if (bRevCurrent && !aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT);
      if (!bRevCurrent && aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);

      return request;
    }
  }

  @NotNull
  public static String getRequestTitle(@NotNull Change change) {
    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();

    assert bRev != null || aRev != null;
    if (bRev != null && aRev != null) {
      FilePath bPath = bRev.getFile();
      FilePath aPath = aRev.getFile();
      if (bPath.equals(aPath)) {
        return DiffRequestFactoryImpl.getContentTitle(bPath);
      }
      else {
        return DiffRequestFactoryImpl.getTitle(bPath, aPath, " -> ");
      }
    }
    else if (bRev != null) {
      return DiffRequestFactoryImpl.getContentTitle(bRev.getFile());
    }
    else {
      return DiffRequestFactoryImpl.getContentTitle(aRev.getFile());
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
  public static DiffContent createContent(@Nullable Project project,
                                          @Nullable ContentRevision revision,
                                          @NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
    try {
      indicator.checkCanceled();

      if (revision == null) return DiffContentFactory.getInstance().createEmpty();
      FilePath filePath = revision.getFile();

      if (revision instanceof CurrentContentRevision) {
        VirtualFile vFile = ((CurrentContentRevision)revision).getVirtualFile();
        if (vFile == null) throw new DiffRequestProducerException("Can't get current revision content");
        return DiffContentFactory.getInstance().create(project, vFile);
      }

      if (revision instanceof BinaryContentRevision) {
        byte[] content = ((BinaryContentRevision)revision).getBinaryContent();
        if (content == null) {
          throw new DiffRequestProducerException("Can't get binary revision content");
        }
        return DiffContentFactory.getInstance().createBinary(project, filePath.getName(), filePath.getFileType(), content);
      }

      if (revision instanceof ByteBackedContentRevision) {
        byte[] revisionContent = ((ByteBackedContentRevision)revision).getContentAsBytes();
        if (revisionContent == null) throw new DiffRequestProducerException("Can't get revision content");
        return FileAwareDocumentContent.create(project, revisionContent, filePath);
      }
      else {
        String revisionContent = revision.getContent();
        if (revisionContent == null) throw new DiffRequestProducerException("Can't get revision content");
        return FileAwareDocumentContent.create(project, revisionContent, filePath);
      }
    }
    catch (IOException e) {
      LOG.info(e);
      throw new DiffRequestProducerException(e);
    }
    catch (VcsException e) {
      LOG.info(e);
      throw new DiffRequestProducerException(e);
    }
  }

  public static void checkContentRevision(@Nullable Project project,
                                          @NotNull ContentRevision rev,
                                          @NotNull UserDataHolder context,
                                          @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
    if (rev.getFile().isDirectory()) {
      throw new DiffRequestProducerException("Can't show diff for directory");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeDiffRequestProducer that = (ChangeDiffRequestProducer)o;

    return myChange.equals(that.myChange);
  }

  @Override
  public int hashCode() {
    return myChange.hashCode();
  }
}
