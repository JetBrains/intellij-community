/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.tryCast;

public class ChangeDiffRequestProducer implements DiffRequestProducer, ChangeDiffRequestChain.Producer {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestProducer.class);

  public static final Key<Change> CHANGE_KEY = Key.create("DiffRequestPresentable.Change");
  public static final Key<Change> TAG_KEY = Key.create("DiffRequestPresentable.Tag");

  public static final String YOUR_VERSION = DiffBundle.message("merge.version.title.our");
  public static final String SERVER_VERSION = DiffBundle.message("merge.version.title.their");
  public static final String BASE_VERSION = DiffBundle.message("merge.version.title.base");
  public static final String MERGED_VERSION = DiffBundle.message("merge.version.title.merged");

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
    return getFilePath().getPath();
  }

  @NotNull
  @Override
  public FilePath getFilePath() {
    return ChangesUtil.getFilePath(myChange);
  }

  @NotNull
  @Override
  public FileStatus getFileStatus() {
    return myChange.getFileStatus();
  }

  @Nullable
  @Override
  public Object getPopupTag() {
    return myChangeContext.get(TAG_KEY);
  }

  public static boolean isEquals(@NotNull Change change1, @NotNull Change change2) {
    if (!Comparing.equal(ChangesUtil.getBeforePath(change1), ChangesUtil.getBeforePath(change2)) ||
        !Comparing.equal(ChangesUtil.getAfterPath(change1), ChangesUtil.getAfterPath(change2))) {
      // we use file paths for hashCode, so removing this check might violate comparison contract
      return false;
    }

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
    if (!Comparing.equal(change1.getFileStatus(), change2.getFileStatus())) return false;
    if (!isEquals(change1.getBeforeRevision(), change2.getBeforeRevision())) return false;
    if (!isEquals(change1.getAfterRevision(), change2.getAfterRevision())) return false;

    if (change1 instanceof ChangeListChange || change2 instanceof ChangeListChange) {
      assert change1 instanceof ChangeListChange && change2 instanceof ChangeListChange;
      String changelistId1 = ((ChangeListChange)change1).getChangeListId();
      String changelistId2 = ((ChangeListChange)change2).getChangeListId();
      if (!Comparing.equal(changelistId1, changelistId2)) return false;
    }

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

  public static int hashCode(@NotNull Change change) {
    return hashCode(change.getBeforeRevision()) + 31 * hashCode(change.getAfterRevision());
  }

  private static int hashCode(@Nullable ContentRevision revision) {
    return revision != null ? revision.getFile().hashCode() : 0;
  }

  @Nullable
  public static ChangeDiffRequestProducer create(@Nullable Project project, @NotNull Change change) {
    return create(project, change, Collections.emptyMap());
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
    catch (ProcessCanceledException | DiffRequestProducerException e) {
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
      return createMergeRequest(project, change, context);
    }

    SimpleDiffRequest request = createSimpleRequest(project, change, context, indicator);

    DiffRequest localRequest = createLocalChangeListRequest(project, change, request);
    if (localRequest != null) return localRequest;

    return request;
  }

  @NotNull
  private static DiffRequest createMergeRequest(@Nullable Project project,
                                                @NotNull Change change,
                                                @NotNull UserDataHolder context) throws DiffRequestProducerException {
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
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          mergeDataRef.set(vcs.getMergeProvider().loadRevisions(finalFile));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      });
      if (!exceptionRef.isNull()) {
        Throwable e = exceptionRef.get();
        if (e instanceof VcsException) throw (VcsException)e;
        ExceptionUtil.rethrow(e);
      }
      MergeData mergeData = mergeDataRef.get();

      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();
      String beforeRevisionTitle = getRevisionTitle(bRev, YOUR_VERSION);
      String afterRevisionTitle = getRevisionTitle(aRev, SERVER_VERSION);

      String title = DiffRequestFactory.getInstance().getTitle(file);
      List<String> titles = ContainerUtil.list(beforeRevisionTitle, BASE_VERSION, afterRevisionTitle);

      DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      List<DiffContent> contents = ContainerUtil.list(
        contentFactory.createFromBytes(project, mergeData.CURRENT, file),
        contentFactory.createFromBytes(project, mergeData.ORIGINAL, file),
        contentFactory.createFromBytes(project, mergeData.LAST, file)
      );

      SimpleDiffRequest request = new SimpleDiffRequest(title, contents, titles);
      MergeUtil.putRevisionInfos(request, mergeData);

      return request;
    }
    catch (VcsException | IOException e) {
      LOG.info(e);
      throw new DiffRequestProducerException(e);
    }
  }

  @NotNull
  private SimpleDiffRequest createSimpleRequest(@Nullable Project project,
                                                @NotNull Change change,
                                                @NotNull UserDataHolder context,
                                                @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
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
    String beforeRevisionTitle = userLeftRevisionTitle != null ? userLeftRevisionTitle : getRevisionTitle(bRev, BASE_VERSION);
    final String userRightRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE);
    String afterRevisionTitle = userRightRevisionTitle != null ? userRightRevisionTitle : getRevisionTitle(aRev, YOUR_VERSION);

    SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);

    boolean bRevCurrent = bRev instanceof CurrentContentRevision;
    boolean aRevCurrent = aRev instanceof CurrentContentRevision;
    if (bRevCurrent && !aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT);
    if (!bRevCurrent && aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);

    return request;
  }

  @Nullable
  private static LocalChangeListDiffRequest createLocalChangeListRequest(@Nullable Project project,
                                                                         @NotNull Change change,
                                                                         @NotNull ContentDiffRequest request) {
    if (project == null) return null;
    if (!(change instanceof ChangeListChange)) return null;
    ChangeListChange changeListChange = (ChangeListChange)change;

    List<DiffContent> contents = request.getContents();
    if (contents.size() != 2) return null;
    DocumentContent content1 = tryCast(Side.LEFT.select(contents), DocumentContent.class);
    DocumentContent content2 = tryCast(Side.RIGHT.select(contents), DocumentContent.class);
    if (content1 == null || content2 == null) return null;

    if (!(content2 instanceof FileContent)) return null;
    VirtualFile virtualFile = ((FileContent)content2).getFile();

    if (!LineStatusTrackerManager.getInstance(project).arePartialChangelistsEnabled(virtualFile)) return null;

    return new LocalChangeListDiffRequest(project, virtualFile, changeListChange.getChangeListId(), changeListChange.getChangeListName(),
                                          request);
  }

  @NotNull
  public static String getRequestTitle(@NotNull Change change) {
    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();
    FilePath bPath = bRev != null ? bRev.getFile() : null;
    FilePath aPath = aRev != null ? aRev.getFile() : null;
    return DiffRequestFactoryImpl.getTitle(bPath, aPath, " -> ");
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
      DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

      if (revision instanceof CurrentContentRevision) {
        VirtualFile vFile = ((CurrentContentRevision)revision).getVirtualFile();
        if (vFile == null || !vFile.isValid()) throw new DiffRequestProducerException("Can't get current revision content");
        return contentFactory.create(project, vFile);
      }

      DiffContent content;
      if (revision instanceof ByteBackedContentRevision) {
        byte[] revisionContent = ((ByteBackedContentRevision)revision).getContentAsBytes();
        if (revisionContent == null) throw new DiffRequestProducerException("Can't get revision content");
        content = contentFactory.createFromBytes(project, revisionContent, filePath);
      }
      else {
        String revisionContent = revision.getContent();
        if (revisionContent == null) throw new DiffRequestProducerException("Can't get revision content");
        content = contentFactory.create(project, revisionContent, filePath);
      }

      content.putUserData(DiffUserDataKeysEx.REVISION_INFO, Pair.create(revision.getFile(), revision.getRevisionNumber()));

      return content;
    }
    catch (IOException | VcsException e) {
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

    ChangeDiffRequestProducer producer = (ChangeDiffRequestProducer)o;
    return isEquals(producer.myChange, myChange);
  }

  @Override
  public int hashCode() {
    return hashCode(myChange);
  }
}
