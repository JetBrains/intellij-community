// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.*;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.impl.DiffEditorTitleDetails;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.requests.UnknownFileTypeDiffRequest;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.diff.impl.DiffTitleWithDetailsCustomizers;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.diff.tools.util.DiffNotifications.createNotificationProvider;
import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.openapi.vcs.changes.patch.PatchDiffRequestFactory.createConflictDiffRequest;
import static com.intellij.openapi.vcs.changes.patch.PatchDiffRequestFactory.createDiffRequest;
import static com.intellij.util.ObjectUtils.chooseNotNull;

@ApiStatus.Internal
public final class DiffShelvedChangesActionProvider implements AnActionExtensionProvider {
  private static final Logger LOG = getInstance(DiffShelvedChangesActionProvider.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ShelvedChangesViewManager.SHELVED_CHANGES_TREE) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    updateAvailability(e);
  }

  public static void updateAvailability(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e.getDataContext()));
    boolean shouldBeHidden = ExperimentalUI.isNewUI() && e.isFromActionToolbar();
    e.getPresentation().setVisible(!shouldBeHidden);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    showShelvedChangesDiff(e.getDataContext());
  }

  public static boolean isEnabled(final DataContext dc) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return false;
    List<ShelvedChangeList> changeLists = ShelvedChangesViewManager.getShelvedLists(dc);
    return changeLists.size() == 1;
  }

  public static void showShelvedChangesDiff(final DataContext dc) {
    showShelvedChangesDiff(dc, false);
  }

  public static @Nullable ListSelection<? extends ChangeDiffRequestChain.Producer> createDiffProducers(@NotNull Project project,
                                                                                                       boolean withLocal,
                                                                                                       ListSelection<ShelvedWrapper> selection) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return null;

    final String base = project.getBasePath();
    if (base == null) {
      LOG.error("No base path for project " + project);
      return null;
    }

    ApplyPatchContext patchContext = new ApplyPatchContext(project.getBaseDir(), 0, false, false);

    return selection.map(s -> {
      return createDiffProducer(project, base, patchContext, s, withLocal);
    });
  }

  public static @Nullable ChangeDiffRequestChain.Producer createDiffProducer(@NotNull Project project,
                                                                             @NotNull ShelvedWrapper shelvedWrapper,
                                                                             boolean withLocal) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return null;

    String base = project.getBasePath();
    if (base == null) {
      LOG.error("No base path for project " + project);
      return null;
    }

    ApplyPatchContext patchContext = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    return createDiffProducer(project, base, patchContext, shelvedWrapper, withLocal);
  }

  private static @Nullable ChangeDiffRequestChain.Producer createDiffProducer(@NotNull Project project,
                                                                              @NotNull String base,
                                                                              @NotNull ApplyPatchContext patchContext,
                                                                              @NotNull ShelvedWrapper shelvedWrapper,
                                                                              boolean withLocal) {
    ShelvedChange textChange = shelvedWrapper.getShelvedChange();
    if (textChange != null) {
      return processTextChange(project, base, patchContext, textChange, withLocal);
    }
    ShelvedBinaryFile binaryChange = shelvedWrapper.getBinaryFile();
    if (binaryChange != null) {
      return processBinaryChange(project, base, binaryChange);
    }
    return null;
  }

  public static void showShelvedChangesDiff(@NotNull DataContext dc, boolean withLocal) {
    Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return;
    showShelvedChangesDiff(project, withLocal, ShelvedChangesViewManager.getSelectedChangesOrAll(dc));
  }

  public static void showShelvedChangesDiff(@NotNull Project project,
                                            boolean withLocal,
                                            ListSelection<ShelvedWrapper> selection) {
    ListSelection<? extends ChangeDiffRequestChain.Producer> diffRequestProducers = createDiffProducers(project, withLocal, selection);
    if (diffRequestProducers == null || diffRequestProducers.isEmpty()) return;

    DiffRequestChain chain = new ChangeDiffRequestChain(diffRequestProducers);
    chain.putUserData(PatchesPreloader.SHELF_PRELOADER, new PatchesPreloader(project));
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME);
  }

  private static ShelveDiffRequestProducer processBinaryChange(@NotNull Project project,
                                                               @NotNull String base,
                                                               @NotNull ShelvedBinaryFile shelvedChange) {
    final File file = new File(base, shelvedChange.AFTER_PATH == null ? shelvedChange.BEFORE_PATH : shelvedChange.AFTER_PATH);
    final FilePath filePath = VcsUtil.getFilePath(file, false);
    return new BinaryShelveDiffRequestProducer(project, shelvedChange, filePath);
  }

  private static ShelveDiffRequestProducer processTextChange(@NotNull Project project,
                                                             @NotNull String base,
                                                             @NotNull ApplyPatchContext patchContext,
                                                             @NotNull ShelvedChange shelvedChange,
                                                             boolean withLocal) {
    final String beforePath = shelvedChange.getBeforePath();
    final String afterPath = shelvedChange.getAfterPath();
    final FilePath filePath = VcsUtil.getFilePath(new File(base, afterPath), false);

    try {
      if (FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
        return new NewFileTextShelveDiffRequestProducer(project, shelvedChange, filePath, withLocal);
      }
      else {
        VirtualFile file = ApplyFilePatchBase.findPatchTarget(patchContext, beforePath, afterPath);
        if (file == null || !file.exists()) throw new FileNotFoundException(beforePath);

        return new TextShelveDiffRequestProducer(project, shelvedChange, filePath, file,
                                                 patchContext, withLocal);
      }
    }
    catch (IOException e) {
      return new PatchShelveDiffRequestProducer(project, shelvedChange, filePath);
    }
  }

  @ApiStatus.Internal
  public final static class PatchesPreloader {
    public static final Key<PatchesPreloader> SHELF_PRELOADER = Key.create("DiffShelvedChangesActionProvider.PatchesPreloader");

    private final Project myProject;
    private final SoftHardCacheMap<Path, PatchInfo> myFilePatchesMap = new SoftHardCacheMap<>(5, 5);
    private final ReadWriteLock myLock = new ReentrantReadWriteLock(true);

    public PatchesPreloader(final Project project) {
      myProject = project;
    }

    public static @NotNull PatchesPreloader getPatchesPreloader(@NotNull Project project, @NotNull UserDataHolder context) {
      PatchesPreloader preloader = context.getUserData(SHELF_PRELOADER);
      if (preloader != null) return preloader;
      return new PatchesPreloader(project);
    }

    @RequiresBackgroundThread
    public @NotNull TextFilePatch getPatch(@NotNull ShelvedChange shelvedChange) throws VcsException {
      return getPatchWithContext(shelvedChange).first;
    }

    @RequiresBackgroundThread
    public @NotNull Pair<TextFilePatch, CommitContext> getPatchWithContext(@NotNull ShelvedChange shelvedChange) throws VcsException {
      Path patchPath = shelvedChange.getPatchPath();
      if (getInfoFromCache(patchPath) == null || isPatchFileChanged(patchPath)) {
        readFilePatchAndUpdateCaches(patchPath);
      }
      PatchInfo patchInfo = getInfoFromCache(patchPath);
      if (patchInfo != null) {
        for (TextFilePatch textFilePatch : patchInfo.myTextFilePatches) {
          if (shelvedChange.getBeforePath().equals(textFilePatch.getBeforeName())) {
            return Pair.create(textFilePatch, patchInfo.myCommitContext);
          }
        }
      }
      throw new VcsException(VcsBundle.message("changes.can.not.find.patch.for.path.in.patch.file", shelvedChange.getBeforePath()));
    }

    private PatchInfo getInfoFromCache(@NotNull Path patchPath) {
      try {
        myLock.readLock().lock();
        return myFilePatchesMap.get(patchPath);
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    private void readFilePatchAndUpdateCaches(@NotNull Path patchPath) throws VcsException {
      try {
        myLock.writeLock().lock();
        CommitContext commitContext = new CommitContext();
        List<TextFilePatch> patches = ShelveChangesManager.loadPatches(myProject, patchPath, commitContext);
        long timestamp = Files.getLastModifiedTime(patchPath).toMillis();
        myFilePatchesMap.put(patchPath, new PatchInfo(patches, commitContext, timestamp));
      }
      catch (IOException | PatchSyntaxException e) {
        throw new VcsException(e);
      }
      finally {
        myLock.writeLock().unlock();
      }
    }

    public boolean isPatchFileChanged(@NotNull Path patchPath) {
      PatchInfo patchInfo = getInfoFromCache(patchPath);
      if (patchInfo == null) {
        return false;
      }

      try {
        return Files.getLastModifiedTime(patchPath).toMillis() != patchInfo.myLoadedTimeStamp;
      }
      catch (IOException e) {
        return false;
      }
    }

    private static final class PatchInfo {
      private final long myLoadedTimeStamp;
      private final @NotNull List<TextFilePatch> myTextFilePatches;
      private final @NotNull CommitContext myCommitContext;

      PatchInfo(@NotNull List<TextFilePatch> patches, @NotNull CommitContext commitContext, long loadedTimeStamp) {
        myTextFilePatches = patches;
        myCommitContext = commitContext;
        myLoadedTimeStamp = loadedTimeStamp;
      }
    }
  }

  private abstract static class ShelveDiffRequestProducer implements ChangeDiffRequestChain.Producer {
    protected final @NotNull FilePath myFilePath;

    ShelveDiffRequestProducer(@NotNull FilePath filePath) {
      myFilePath = filePath;
    }

    public @Nullable ShelvedChange getTextChange() {
      return null;
    }

    public @Nullable ShelvedBinaryFile getBinaryChange() {
      return null;
    }

    @Override
    public @NotNull String getName() {
      return FileUtil.toSystemDependentName(getFilePath().getPath());
    }

    public @NotNull @NlsContexts.DialogTitle String getRequestTitle() {
      ShelvedChange textChange = getTextChange();
      Change change = textChange != null ? textChange.getChange() : null;

      return change != null ? ChangeDiffRequestProducer.getRequestTitle(change) : getName();
    }

    @Override
    public @NotNull FilePath getFilePath() {
      return myFilePath;
    }
  }

  private static class BinaryShelveDiffRequestProducer extends ShelveDiffRequestProducer {
    private final @NotNull Project myProject;
    private final @NotNull ShelvedBinaryFile myBinaryChange;

    BinaryShelveDiffRequestProducer(@NotNull Project project,
                                    @NotNull ShelvedBinaryFile change,
                                    @NotNull FilePath filePath) {
      super(filePath);
      myBinaryChange = change;
      myProject = project;
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      Change change = myBinaryChange.createChange(myProject);
      return createDiffRequest(myProject, change, getName(), context, indicator);
    }

    @Override
    public @NotNull FileStatus getFileStatus() {
      return myBinaryChange.getFileStatus();
    }

    @Override
    public @NotNull ShelvedBinaryFile getBinaryChange() {
      return myBinaryChange;
    }
  }

  private static class PatchShelveDiffRequestProducer extends BaseTextShelveDiffRequestProducer {
    PatchShelveDiffRequestProducer(@NotNull Project project,
                                   @NotNull ShelvedChange change,
                                   @NotNull FilePath filePath) {
      super(project, change, filePath);
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      try {
        PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
        TextFilePatch patch = preloader.getPatch(myChange);

        String leftTitle = DiffBundle.message("merge.version.title.base");
        String rightTitle = VcsBundle.message("shelve.shelved.version");
        PatchDiffRequest request = new PatchDiffRequest(patch, getRequestTitle(), leftTitle, rightTitle);
        DiffUtil.addNotification(createNotificationProvider(DiffBundle.message("cannot.find.file.error", getFilePath())), request);
        return request;
      }
      catch (VcsException e) {
        throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for", getFilePath()), e);
      }
    }
  }

  private static class NewFileTextShelveDiffRequestProducer extends BaseTextShelveDiffRequestProducer {
    private final boolean myWithLocal;

    NewFileTextShelveDiffRequestProducer(@NotNull Project project,
                                         @NotNull ShelvedChange change,
                                         @NotNull FilePath filePath,
                                         boolean withLocal) {
      super(project, change, filePath);
      myWithLocal = withLocal;
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      VirtualFile file = myFilePath.getVirtualFile();
      if (myWithLocal && file != null) {
        try {
          PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
          TextFilePatch patch = preloader.getPatch(myChange);

          DiffContentFactory contentFactory = DiffContentFactory.getInstance();
          DiffContent leftContent = contentFactory.create(myProject, file);
          DiffContent rightContent = contentFactory.create(myProject, patch.getSingleHunkPatchText(), file);

          String leftTitle = DiffBundle.message("merge.version.title.current");
          String rightTitle = VcsBundle.message("shelve.shelved.version");
          DiffRequest request = new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, leftTitle, rightTitle);
          List<DiffEditorTitleCustomizer> titleCustomizers =
            DiffTitleWithDetailsCustomizers.getTitleCustomizers(myProject, myChange.getChange(), leftTitle, rightTitle);
          return DiffUtil.addTitleCustomizers(request, titleCustomizers);
        }
        catch (VcsException e) {
          throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for", getFilePath()), e);
        }
      }
      else {
        return createDiffRequest(myProject, myChange.getChange(), getName(), context, indicator);
      }
    }
  }

  private static class TextShelveDiffRequestProducer extends BaseTextShelveDiffRequestProducer {
    private final @NotNull VirtualFile myFile;
    private final @NotNull ApplyPatchContext myPatchContext;
    private final boolean myWithLocal;

    TextShelveDiffRequestProducer(@NotNull Project project,
                                  @NotNull ShelvedChange change,
                                  @NotNull FilePath filePath,
                                  @NotNull VirtualFile file,
                                  @NotNull ApplyPatchContext patchContext,
                                  boolean withLocal) {
      super(project, change, filePath);
      myFile = file;
      myPatchContext = patchContext;
      myWithLocal = withLocal;
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      if (FileTypeRegistry.getInstance().isFileOfType(myFile, UnknownFileType.INSTANCE)) {
        return new UnknownFileTypeDiffRequest(myFile, getName());
      }

      try {
        PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
        Pair<TextFilePatch, CommitContext> pair = preloader.getPatchWithContext(myChange);
        TextFilePatch patch = pair.first;
        CommitContext commitContext = pair.second;

        if (patch.isDeletedFile()) {
          return createDiffRequestForDeleted(patch);
        }
        else {
          String path = chooseNotNull(patch.getAfterName(), patch.getBeforeName());
          CharSequence baseContents = BaseRevisionTextPatchEP.getBaseContent(myProject, path, commitContext);

          ApplyPatchForBaseRevisionTexts texts =
            ApplyPatchForBaseRevisionTexts.create(myProject, myFile, myPatchContext.getPathBeforeRename(myFile), patch, baseContents);

          if (texts.isBaseRevisionLoaded()) {
            assert !texts.isAppliedSomehow();
            return createDiffRequestUsingBase(texts);
          }
          else {
            return createDiffRequestUsingLocal(texts, patch, context, indicator);
          }
        }
      }
      catch (VcsException e) {
        throw new DiffRequestProducerException(VcsBundle.message("changes.error.can.t.show.diff.for", getFilePath()), e);
      }
    }

    private @NotNull DiffRequest createDiffRequestForDeleted(@NotNull TextFilePatch patch) {
      DiffContentFactory contentFactory = DiffContentFactory.getInstance();

      DiffContent leftContent;
      String leftTitle;
      if (myWithLocal) {
        leftContent = contentFactory.create(myProject, myFile);
        leftTitle = DiffBundle.message("merge.version.title.current");
      }
      else {
        leftContent = contentFactory.create(myProject, patch.getSingleHunkPatchText(), myFile);
        leftTitle = VcsBundle.message("shelve.shelved.version");
      }

      DiffContent rightContent = contentFactory.createEmpty();
      String rightTitle = null;

      DiffRequest request = new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, leftTitle, rightTitle);
      return DiffUtil.addTitleCustomizers(
        request,
        DiffEditorTitleDetails.create(myProject, VcsUtil.getFilePath(myFile), leftTitle).getCustomizer(),
        DiffEditorTitleCustomizer.EMPTY
      );
    }

    private @NotNull DiffRequest createDiffRequestUsingBase(@NotNull ApplyPatchForBaseRevisionTexts texts) {
      DiffContentFactory contentFactory = DiffContentFactory.getInstance();

      DiffContent leftContent;
      String leftTitle;
      if (myWithLocal) {
        leftContent = contentFactory.create(myProject, myFile);
        leftTitle = DiffBundle.message("merge.version.title.current");
      }
      else {
        leftContent = contentFactory.create(myProject, Objects.requireNonNull(texts.getBase()), myFile);
        leftTitle = DiffBundle.message("merge.version.title.base");
      }

      DiffContent rightContent = contentFactory.create(myProject, texts.getPatched(), myFile);

      DiffRequest request =
        new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, leftTitle, VcsBundle.message("shelve.shelved.version"));

      return DiffUtil.addTitleCustomizers(
        request,
        DiffEditorTitleDetails.create(myProject, VcsUtil.getFilePath(myFile), leftTitle).getCustomizer(),
        DiffEditorTitleCustomizer.EMPTY
      );
    }

    private DiffRequest createDiffRequestUsingLocal(@NotNull ApplyPatchForBaseRevisionTexts texts,
                                                    @NotNull TextFilePatch patch,
                                                    @NotNull UserDataHolder context,
                                                    @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      DiffRequest diffRequest = myChange.isConflictingChange()
                                ? createConflictDiffRequest(myProject, myFile, patch, VcsBundle.message("shelve.shelved.version"),
                                                            texts, getName())
                                : createDiffRequest(myProject, myChange.getChange(), getName(), context, indicator);
      if (!myWithLocal) {
        DiffUtil.addNotification(createNotificationProvider(
          VcsBundle.message("shelve.base.content.not.found.or.not.applicable.error")), diffRequest);
      }
      return diffRequest;
    }
  }

  private abstract static class BaseTextShelveDiffRequestProducer extends ShelveDiffRequestProducer {
    protected final @NotNull Project myProject;
    protected final @NotNull ShelvedChange myChange;

    BaseTextShelveDiffRequestProducer(@NotNull Project project,
                                      @NotNull ShelvedChange change,
                                      @NotNull FilePath filePath) {
      super(filePath);
      myChange = change;
      myProject = project;
    }

    @Override
    public @NotNull FileStatus getFileStatus() {
      return myChange.getFileStatus();
    }

    @Override
    public @NotNull ShelvedChange getTextChange() {
      return myChange;
    }
  }
}

