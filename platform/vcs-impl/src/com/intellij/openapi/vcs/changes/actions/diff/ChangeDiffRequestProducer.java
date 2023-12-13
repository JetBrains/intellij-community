// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.*;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffRequest;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeUtils;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.diff.DiffRequestFactoryImpl.DIFF_TITLE_RENAME_SEPARATOR;
import static com.intellij.util.ObjectUtils.tryCast;

public final class ChangeDiffRequestProducer implements DiffRequestProducer, ChangeDiffRequestChain.Producer {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestProducer.class);

  public static final Key<Change> CHANGE_KEY = Key.create("DiffRequestPresentable.Change");
  public static final Key<Change> TAG_KEY = Key.create("DiffRequestPresentable.Tag");

  private final @Nullable Project myProject;
  private final @NotNull Change myChange;
  private final @NotNull Map<Key<?>, Object> myChangeContext;

  private ChangeDiffRequestProducer(@Nullable Project project, @NotNull Change change, @NotNull Map<Key<?>, Object> changeContext) {
    myChange = change;
    myProject = project;
    myChangeContext = changeContext;
  }

  public @NotNull Change getChange() {
    return myChange;
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull String getName() {
    return getFilePath().getPath();
  }

  @Override
  public @NotNull FilePath getFilePath() {
    return ChangesUtil.getFilePath(myChange);
  }

  @Override
  public @NotNull FileStatus getFileStatus() {
    return myChange.getFileStatus();
  }

  @Override
  public @Nullable ChangesBrowserNode.Tag getTag() {
    return tryCast(myChangeContext.get(TAG_KEY), ChangesBrowserNode.Tag.class);
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
      if (!Objects.equals(changelistId1, changelistId2)) return false;
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

  public static @Nullable ChangeDiffRequestProducer create(@Nullable Project project, @NotNull Change change) {
    return create(project, change, null);
  }

  public static @Nullable ChangeDiffRequestProducer create(@Nullable Project project,
                                                           @NotNull Change change,
                                                           @Nullable Map<Key<?>, Object> changeContext) {
    if (!canCreate(project, change)) return null;
    return new ChangeDiffRequestProducer(project, change, ContainerUtil.notNullize(changeContext));
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

  @Override
  public @NotNull DiffRequest process(@NotNull UserDataHolder context,
                                      @NotNull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    try {
      return loadCurrentContents(context, indicator);
    }
    catch (ProcessCanceledException | DiffRequestProducerException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
      throw new DiffRequestProducerException(e);
    }
  }

  private @NotNull DiffRequest loadCurrentContents(@NotNull UserDataHolder context,
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
      if (request == null) {
        request = createRequest(myProject, myChange, context, indicator);
      }
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

    for (Map.Entry<Key<?>, Object> entry : myChangeContext.entrySet()) {
      //noinspection unchecked,rawtypes
      request.putUserData((Key)entry.getKey(), entry.getValue());
    }

    DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_CHANGE, myChange);

    return request;
  }

  private @NotNull DiffRequest createRequest(@Nullable Project project,
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

  private static @NotNull DiffRequest createMergeRequest(@Nullable Project project,
                                                         @NotNull Change change,
                                                         @NotNull UserDataHolder context) throws DiffRequestProducerException {
    FilePath path = ChangesUtil.getFilePath(change);
    VirtualFile file = path.getVirtualFile();
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
    }
    if (file == null) throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.merge.file.not.found"));

    if (project == null) {
      throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.merge.project.not.found"));
    }
    final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
    if (vcs == null || vcs.getMergeProvider() == null) {
      throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.merge.operation.not.supported"));
    }
    try {
      MergeData mergeData = vcs.getMergeProvider().loadRevisions(file);

      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();
      String beforeRevisionTitle = getRevisionTitle(bRev, getYourVersion());
      String afterRevisionTitle = getRevisionTitle(aRev, getServerVersion());

      String title = DiffRequestFactory.getInstance().getTitle(file);
      List<String> titles = Arrays.asList(beforeRevisionTitle, getBaseVersion(), afterRevisionTitle);

      DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      List<DiffContent> contents = Arrays.asList(contentFactory.createFromBytes(project, mergeData.CURRENT, file),
                                                 contentFactory.createFromBytes(project, mergeData.ORIGINAL, file),
                                                 contentFactory.createFromBytes(project, mergeData.LAST, file));

      SimpleDiffRequest request = new SimpleDiffRequest(title, contents, titles);
      MergeUtils.putRevisionInfos(request, mergeData);

      return request;
    }
    catch (VcsException | IOException e) {
      LOG.info(e);
      throw new DiffRequestProducerException(e);
    }
  }

  private @NotNull SimpleDiffRequest createSimpleRequest(@Nullable Project project,
                                                         @NotNull Change change,
                                                         @NotNull UserDataHolder context,
                                                         @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();

    if (bRev == null && aRev == null) {
      LOG.warn("Both revision contents are empty");
      throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.content.not.found"));
    }
    if (bRev != null) checkContentRevision(project, bRev, context, indicator);
    if (aRev != null) checkContentRevision(project, aRev, context, indicator);

    final String editorTabTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_EDITOR_TAB_TITLE);
    String title = editorTabTitle == null ? getRequestTitle(change) : editorTabTitle;

    indicator.setIndeterminate(true);
    DiffContent content1 = createContent(project, bRev, context, indicator);
    DiffContent content2 = createContent(project, aRev, context, indicator);

    final String userLeftRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE);
    String beforeRevisionTitle = userLeftRevisionTitle != null ? userLeftRevisionTitle : getRevisionTitle(bRev, getBaseVersion());
    final String userRightRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE);
    String afterRevisionTitle = userRightRevisionTitle != null ? userRightRevisionTitle : getRevisionTitle(aRev, getYourVersion());

    SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);

    boolean bRevCurrent = bRev instanceof CurrentContentRevision;
    boolean aRevCurrent = aRev instanceof CurrentContentRevision;
    if (bRevCurrent && !aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT);
    if (!bRevCurrent && aRevCurrent) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);

    return request;
  }

  private static @Nullable LocalChangeListDiffRequest createLocalChangeListRequest(@Nullable Project project,
                                                                                   @NotNull Change change,
                                                                                   @NotNull ContentDiffRequest request) {
    if (project == null) return null;
    if (!(change instanceof ChangeListChange changeListChange)) return null;

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

  public static @NotNull @Nls String getRequestTitle(@NotNull Change change) {
    FilePath bPath = ChangesUtil.getBeforePath(change);
    FilePath aPath = ChangesUtil.getAfterPath(change);
    return DiffRequestFactoryImpl.getTitle(bPath, aPath, DIFF_TITLE_RENAME_SEPARATOR);
  }

  public static @NotNull @Nls String getRevisionTitle(@Nullable ContentRevision revision, @NotNull @Nls String defaultValue) {
    if (revision == null) {
      return defaultValue;
    }
    String title = revision.getRevisionNumber().asString();
    return title.isEmpty() ? defaultValue : title;
  }

  public static @NotNull DiffContent createContent(@Nullable Project project,
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
        if (vFile == null || !vFile.isValid()) {
          throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.cant.load.revision.content"));
        }
        return contentFactory.create(project, vFile);
      }

      DiffContent content;
      if (revision instanceof ByteBackedContentRevision) {
        byte[] revisionContent = ((ByteBackedContentRevision)revision).getContentAsBytes();
        if (revisionContent == null) {
          throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.cant.load.revision.content"));
        }
        content = contentFactory.createFromBytes(project, revisionContent, filePath);
      }
      else {
        String revisionContent = revision.getContent();
        if (revisionContent == null) {
          throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.cant.load.revision.content"));
        }
        content = contentFactory.create(project, revisionContent, filePath);
      }

      content.putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(revision.getFile(), revision.getRevisionNumber()));

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
      throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.cant.show.for.directory"));
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

  @Nls
  public static String getYourVersion() {
    return DiffBundle.message("merge.version.title.our");
  }

  @Nls
  public static String getServerVersion() {
    return DiffBundle.message("merge.version.title.their");
  }

  @Nls
  public static String getBaseVersion() {
    return DiffBundle.message("merge.version.title.base");
  }

  @Nls
  public static String getMergedVersion() {
    return DiffBundle.message("merge.version.title.merged");
  }
}
