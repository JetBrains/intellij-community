// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.PlainSimplePatchApplier;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest;
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.PatchesPreloader;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.history.DiffTitleFilePathCustomizer;
import com.intellij.openapi.vcs.history.DiffTitleFilePathCustomizer.RevisionWithTitle;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public class ShelvedWrapperDiffRequestProducer implements DiffRequestProducer, ChangeDiffRequestChain.Producer {
  private final Project myProject;
  private final ShelvedWrapper myChange;

  public ShelvedWrapperDiffRequestProducer(@NotNull Project project, @NotNull ShelvedWrapper change) {
    myProject = project;
    myChange = change;
  }

  @NotNull
  public ShelvedWrapper getWrapper() {
    return myChange;
  }

  @Nls
  @Override
  public @NotNull String getName() {
    return myChange.getRequestName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ShelvedWrapperDiffRequestProducer producer = (ShelvedWrapperDiffRequestProducer)o;
    return Objects.equals(myChange, producer.myChange);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myChange);
  }

  @Override
  public @NotNull DiffRequest process(@NotNull UserDataHolder context,
                                      @NotNull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    String title = myChange.getRequestName();
    try {
      ShelvedChange shelvedChange = myChange.getShelvedChange();
      if (shelvedChange != null) {
        return createTextShelveRequest(shelvedChange, context, title);
      }

      ShelvedBinaryFile binaryFile = myChange.getBinaryFile();
      if (binaryFile != null) {
        return createBinaryShelveRequest(binaryFile, title);
      }

      throw new IllegalStateException("Empty shelved wrapper: " + myChange);
    }
    catch (VcsException | IOException e) {
      throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for", title), e);
    }
  }

  @NotNull
  private DiffRequest createTextShelveRequest(@NotNull ShelvedChange shelvedChange,
                                              @NotNull UserDataHolder context,
                                              @Nullable @Nls String title)
    throws VcsException {
    PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
    Pair<TextFilePatch, CommitContext> pair = preloader.getPatchWithContext(shelvedChange);
    TextFilePatch patch = pair.first;
    CommitContext commitContext = pair.second;

    FilePath contextFilePath = getContextFilePath(shelvedChange);

    String leftTitle = DiffBundle.message("merge.version.title.base");
    String rightTitle = VcsBundle.message("shelve.shelved.version");

    DiffRequest request = createTextShelveRequest(title, patch, contextFilePath, leftTitle, rightTitle, commitContext);

    Change change = shelvedChange.getChange();
    List<DiffEditorTitleCustomizer> titleCustomizers = DiffTitleFilePathCustomizer.getTitleCustomizers(myProject, change, leftTitle, rightTitle);
    return DiffUtil.addTitleCustomizers(request, titleCustomizers);
  }

  @NotNull
  private DiffRequest createTextShelveRequest(@Nls @Nullable String title,
                                              TextFilePatch patch,
                                              FilePath contextFilePath,
                                              @Nls String leftTitle,
                                              @Nls String rightTitle,
                                              CommitContext commitContext) {
    DiffContentFactoryEx factory = DiffContentFactoryEx.getInstanceEx();

    if (patch.isDeletedFile() || patch.isNewFile()) {
      DiffContent shelfContent = factory.create(myProject, patch.getSingleHunkPatchText(), contextFilePath);
      DiffContent emptyContent = factory.createEmpty();

      DiffContent leftContent = patch.isDeletedFile() ? shelfContent : emptyContent;
      DiffContent rightContent = !patch.isDeletedFile() ? shelfContent : emptyContent;

      return new SimpleDiffRequest(title, leftContent, rightContent, leftTitle, rightTitle);
    }

    String path = chooseNotNull(patch.getAfterName(), patch.getBeforeName());
    CharSequence baseContents = BaseRevisionTextPatchEP.getBaseContent(myProject, path, commitContext);
    if (baseContents != null) {
      String patchedContent = PlainSimplePatchApplier.apply(baseContents, patch.getHunks());
      if (patchedContent != null) {
        DiffContent leftContent = factory.create(myProject, baseContents.toString(), contextFilePath);
        DiffContent rightContent = factory.create(myProject, patchedContent, contextFilePath);

        return new SimpleDiffRequest(title, leftContent, rightContent, leftTitle, rightTitle);
      }
    }

    return new PatchDiffRequest(patch, title, leftTitle, rightTitle);
  }

  @Override
  public @NotNull FilePath getFilePath() {
    return myChange.getFilePath();
  }

  @Override
  public @NotNull FileStatus getFileStatus() {
    return myChange.getFileStatus();
  }

  @NotNull
  private static FilePath getContextFilePath(@NotNull ShelvedChange shelvedChange) {
    Change change = shelvedChange.getChange();
    if (change.getType() == Change.Type.MOVED) {
      FilePath bPath = requireNonNull(ChangesUtil.getBeforePath(change));
      FilePath aPath = requireNonNull(ChangesUtil.getAfterPath(change));
      if (bPath.getVirtualFile() != null) return bPath;
      if (aPath.getVirtualFile() != null) return aPath;
      return bPath;
    }
    return ChangesUtil.getFilePath(change);
  }

  @NotNull
  private SimpleDiffRequest createBinaryShelveRequest(@NotNull ShelvedBinaryFile binaryFile, @Nullable @Nls String title)
    throws DiffRequestProducerException, VcsException, IOException {
    DiffContentFactory factory = DiffContentFactory.getInstance();
    if (binaryFile.AFTER_PATH == null) {
      throw new DiffRequestProducerException(VcsBundle.message("changes.error.content.for.0.was.removed", title));
    }

    byte[] binaryContent = binaryFile.createBinaryContentRevision(myProject).getBinaryContent();
    FilePath filePath = VcsUtil.getFilePath(binaryFile.SHELVED_PATH, false);
    DiffContent shelfContent = factory.createFromBytes(myProject, binaryContent, filePath);
    return new SimpleDiffRequest(title, factory.createEmpty(), shelfContent, null, null);
  }
}
