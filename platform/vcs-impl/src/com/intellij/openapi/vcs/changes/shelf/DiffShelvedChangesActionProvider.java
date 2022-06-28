// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.requests.UnknownFileTypeDiffRequest;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
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
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.vcsUtil.VcsUtil;
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

public final class DiffShelvedChangesActionProvider implements AnActionExtensionProvider {
  private static final Logger LOG = getInstance(DiffShelvedChangesActionProvider.class);

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

  public static @Nullable ListSelection<? extends ChangeDiffRequestChain.Producer> createDiffProducers(@NotNull DataContext dc, boolean withLocal){
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return null;

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return null;

    final String base = project.getBasePath();
    if (base == null) {
      LOG.error("No base path for project " + project);
      return null;
    }

    ListSelection<ShelvedWrapper> wrappers = ShelvedChangesViewManager.getSelectedChangesOrAll(dc);

    ApplyPatchContext patchContext = new ApplyPatchContext(project.getBaseDir(), 0, false, false);

    return wrappers.map(s -> {
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

    ListSelection<? extends ChangeDiffRequestChain.Producer> diffRequestProducers = createDiffProducers(dc, withLocal);
    if (diffRequestProducers == null || diffRequestProducers.isEmpty()) return;

    DiffRequestChain chain = new ChangeDiffRequestChain(diffRequestProducers.getList(), diffRequestProducers.getSelectedIndex());
    chain.putUserData(PatchesPreloader.SHELF_PRELOADER, new PatchesPreloader(project));
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME);
  }

  private static ShelveDiffRequestProducer processBinaryChange(@NotNull Project project,
                                                               @NotNull String base,
                                                               @NotNull ShelvedBinaryFile shelvedChange) {
    final File file = new File(base, shelvedChange.AFTER_PATH == null ? shelvedChange.BEFORE_PATH : shelvedChange.AFTER_PATH);
    final FilePath filePath = VcsUtil.getFilePath(file);
    return new BinaryShelveDiffRequestProducer(project, shelvedChange, filePath);
  }

  private static ShelveDiffRequestProducer processTextChange(@NotNull Project project,
                                                             @NotNull String base,
                                                             @NotNull ApplyPatchContext patchContext,
                                                             @NotNull ShelvedChange shelvedChange,
                                                             boolean withLocal) {
    final String beforePath = shelvedChange.getBeforePath();
    final String afterPath = shelvedChange.getAfterPath();
    final FilePath filePath = VcsUtil.getFilePath(new File(base, afterPath == null ? beforePath : afterPath));

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

  /**
   * Simple way to reuse patch parser from GPA ->  apply onto empty text
   */
  @NotNull
  static AppliedTextPatch createAppliedTextPatch(@NotNull TextFilePatch patch) {
    final GenericPatchApplier applier = new GenericPatchApplier("", patch.getHunks());
    applier.execute();
    return AppliedTextPatch.create(applier.getAppliedInfo());
  }

  final static class PatchesPreloader {
    public static final Key<PatchesPreloader> SHELF_PRELOADER = Key.create("DiffShelvedChangesActionProvider.PatchesPreloader");

    private final Project myProject;
    private final SoftHardCacheMap<Path, PatchInfo> myFilePatchesMap = new SoftHardCacheMap<>(5, 5);
    private final ReadWriteLock myLock = new ReentrantReadWriteLock(true);

    PatchesPreloader(final Project project) {
      myProject = project;
    }

    @NotNull
    public static PatchesPreloader getPatchesPreloader(@NotNull Project project, @NotNull UserDataHolder context) {
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
      @NotNull private final List<TextFilePatch> myTextFilePatches;
      @NotNull private final CommitContext myCommitContext;

      PatchInfo(@NotNull List<TextFilePatch> patches, @NotNull CommitContext commitContext, long loadedTimeStamp) {
        myTextFilePatches = patches;
        myCommitContext = commitContext;
        myLoadedTimeStamp = loadedTimeStamp;
      }
    }
  }

  private static abstract class ShelveDiffRequestProducer implements ChangeDiffRequestChain.Producer {
    @NotNull protected final FilePath myFilePath;

    ShelveDiffRequestProducer(@NotNull FilePath filePath) {
      myFilePath = filePath;
    }

    @Nullable
    public ShelvedChange getTextChange() {
      return null;
    }

    @Nullable
    public ShelvedBinaryFile getBinaryChange() {
      return null;
    }

    @NotNull
    @Override
    public String getName() {
      return FileUtil.toSystemDependentName(getFilePath().getPath());
    }

    @NotNull
    @NlsContexts.DialogTitle
    public String getRequestTitle() {
      ShelvedChange textChange = getTextChange();
      Change change = textChange != null ? textChange.getChange() : null;

      return change != null ? ChangeDiffRequestProducer.getRequestTitle(change) : getName();
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFilePath;
    }
  }

  private static class BinaryShelveDiffRequestProducer extends ShelveDiffRequestProducer {
    @NotNull private final Project myProject;
    @NotNull private final ShelvedBinaryFile myBinaryChange;

    BinaryShelveDiffRequestProducer(@NotNull Project project,
                                           @NotNull ShelvedBinaryFile change,
                                           @NotNull FilePath filePath) {
      super(filePath);
      myBinaryChange = change;
      myProject = project;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      Change change = myBinaryChange.createChange(myProject);
      return createDiffRequest(myProject, change, getName(), context, indicator);
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myBinaryChange.getFileStatus();
    }

    @NotNull
    @Override
    public ShelvedBinaryFile getBinaryChange() {
      return myBinaryChange;
    }
  }

  private static class PatchShelveDiffRequestProducer extends BaseTextShelveDiffRequestProducer {
    PatchShelveDiffRequestProducer(@NotNull Project project,
                                   @NotNull ShelvedChange change,
                                   @NotNull FilePath filePath) {
      super(project, change, filePath);
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      try {
        PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
        TextFilePatch patch = preloader.getPatch(myChange);
        AppliedTextPatch appliedTextPatch = createAppliedTextPatch(patch);
        PatchDiffRequest request = new PatchDiffRequest(appliedTextPatch, getRequestTitle(),
                                                        VcsBundle.message("patch.apply.conflict.patch"));
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

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      VirtualFile file = myFilePath.getVirtualFile();
      if (myWithLocal && file != null) {
        try {
          PatchesPreloader preloader = PatchesPreloader.getPatchesPreloader(myProject, context);
          TextFilePatch patch = preloader.getPatch(myChange);

          DiffContentFactory contentFactory = DiffContentFactory.getInstance();
          DiffContent leftContent = contentFactory.create(myProject, file);
          DiffContent rightContent = contentFactory.create(myProject, patch.getSingleHunkPatchText(), file);

          return new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, DiffBundle.message("merge.version.title.current"),
                                       VcsBundle.message("shelve.shelved.version"));
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
    @NotNull private final VirtualFile myFile;
    @NotNull private final ApplyPatchContext myPatchContext;
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

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
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
          CharSequence baseContents = PatchEP.EP_NAME.findExtensionOrFail(BaseRevisionTextPatchEP.class)
            .provideContent(myProject, path, commitContext);

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

    @NotNull
    private DiffRequest createDiffRequestForDeleted(@NotNull TextFilePatch patch) {
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

      return new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, leftTitle, rightTitle);
    }

    @NotNull
    private DiffRequest createDiffRequestUsingBase(@NotNull ApplyPatchForBaseRevisionTexts texts) {
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
      return new SimpleDiffRequest(getRequestTitle(), leftContent, rightContent, leftTitle, VcsBundle.message("shelve.shelved.version"));
    }

    private DiffRequest createDiffRequestUsingLocal(@NotNull ApplyPatchForBaseRevisionTexts texts,
                                                    @NotNull TextFilePatch patch,
                                                    @NotNull UserDataHolder context,
                                                    @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      DiffRequest diffRequest = myChange.isConflictingChange()
                                ? createConflictDiffRequest(myProject, myFile, patch, VcsBundle.message("shelve.shelved.version"), texts, getName())
                                : createDiffRequest(myProject, myChange.getChange(), getName(), context, indicator);
      if (!myWithLocal) {
        DiffUtil.addNotification(createNotificationProvider(
          VcsBundle.message("shelve.base.content.not.found.or.not.applicable.error")), diffRequest);
      }
      return diffRequest;
    }
  }

  private static abstract class BaseTextShelveDiffRequestProducer extends ShelveDiffRequestProducer {
    @NotNull protected final Project myProject;
    @NotNull protected final ShelvedChange myChange;

    BaseTextShelveDiffRequestProducer(@NotNull Project project,
                                             @NotNull ShelvedChange change,
                                             @NotNull FilePath filePath) {
      super(filePath);
      myChange = change;
      myProject = project;
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myChange.getFileStatus();
    }

    @NotNull
    @Override
    public ShelvedChange getTextChange() {
      return myChange;
    }
  }
}

