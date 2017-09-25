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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.requests.UnknownFileTypeDiffRequest;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.diff.tools.util.DiffNotifications.createNotification;
import static com.intellij.openapi.vcs.changes.patch.PatchDiffRequestFactory.createConflictDiffRequest;
import static com.intellij.openapi.vcs.changes.patch.PatchDiffRequestFactory.createDiffRequest;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.chooseNotNull;

public class DiffShelvedChangesActionProvider implements AnActionExtensionProvider {
  private static final String DIFF_WITH_BASE_ERROR = "Base content not found or not applicable.";
  public static final String SHELVED_VERSION = "Shelved Version";
  public static final String BASE_VERSION = "Base Version";
  public static final String CURRENT_VERSION = "Current Version";

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY) != null ||
           e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
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

  public static void showShelvedChangesDiff(final DataContext dc, boolean withLocal) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    List<ShelvedChangeList> changeLists = ShelvedChangesViewManager.getShelvedLists(dc);
    ShelvedChangeList changeList = assertNotNull(ContainerUtil.getFirstItem(changeLists));

    final List<ShelvedChange> textChanges = changeList.getChanges(project);
    final List<ShelvedBinaryFile> binaryChanges = changeList.getBinaryFiles();

    final List<MyDiffRequestProducer> diffRequestProducers = new ArrayList<>();

    processTextChanges(project, textChanges, diffRequestProducers, withLocal);
    processBinaryFiles(project, binaryChanges, diffRequestProducers);

    Collections.sort(diffRequestProducers, ChangeDiffRequestComparator.getInstance());

    // selected changes inside lists
    final Set<Object> selectedChanges = new HashSet<>();
    selectedChanges.addAll(ShelvedChangesViewManager.getShelveChanges(dc));
    selectedChanges.addAll(ShelvedChangesViewManager.getBinaryShelveChanges(dc));

    int index = 0;
    for (int i = 0; i < diffRequestProducers.size(); i++) {
      MyDiffRequestProducer producer = diffRequestProducers.get(i);
      if (selectedChanges.contains(producer.getBinaryChange()) || selectedChanges.contains(producer.getTextChange())) {
        index = i;
        break;
      }
    }

    DiffRequestChain chain = new ChangeDiffRequestChain(diffRequestProducers, index);
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME);
  }

  private static class ChangeDiffRequestComparator implements Comparator<DiffRequestProducer> {
    private final static ChangeDiffRequestComparator ourInstance = new ChangeDiffRequestComparator();

    public static ChangeDiffRequestComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(DiffRequestProducer o1, DiffRequestProducer o2) {
      return FilePathsHelper.convertPath(o1.getName()).compareTo(FilePathsHelper.convertPath(o2.getName()));
    }
  }

  private static void processBinaryFiles(@NotNull final Project project,
                                         @NotNull List<ShelvedBinaryFile> files,
                                         @NotNull List<MyDiffRequestProducer> diffRequestProducers) {
    final String base = project.getBaseDir().getPath();
    for (final ShelvedBinaryFile shelvedChange : files) {
      final File file = new File(base, shelvedChange.AFTER_PATH == null ? shelvedChange.BEFORE_PATH : shelvedChange.AFTER_PATH);
      final FilePath filePath = VcsUtil.getFilePath(file);
      diffRequestProducers.add(new MyDiffRequestProducer(shelvedChange, filePath) {
        @NotNull
        @Override
        public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
          throws DiffRequestProducerException, ProcessCanceledException {
          Change change = shelvedChange.createChange(project);
          return createDiffRequest(project, change, getName(), context, indicator);
        }
      });
    }
  }

  private static void processTextChanges(@NotNull final Project project,
                                         @NotNull List<ShelvedChange> changesFromFirstList,
                                         @NotNull List<MyDiffRequestProducer> diffRequestProducers,
                                         boolean withLocal) {
    final String base = project.getBasePath();
    final ApplyPatchContext patchContext = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    final PatchesPreloader preloader = new PatchesPreloader(project);
    final CommitContext commitContext = new CommitContext();
    for (final ShelvedChange shelvedChange : changesFromFirstList) {
      final String beforePath = shelvedChange.getBeforePath();
      final String afterPath = shelvedChange.getAfterPath();
      final FilePath filePath = VcsUtil.getFilePath(new File(base, afterPath == null ? beforePath : afterPath));
      final boolean isNewFile = FileStatus.ADDED.equals(shelvedChange.getFileStatus());

      final VirtualFile file; // isNewFile -> parent directory, !isNewFile -> file
      try {
        file = ApplyFilePatchBase.findPatchTarget(patchContext, beforePath, afterPath, isNewFile);
        if (!isNewFile && (file == null || !file.exists())) throw new FileNotFoundException(beforePath);
      }
      catch (IOException e) {
        diffRequestProducers.add(new MyDiffRequestProducer(shelvedChange, filePath) {
          @NotNull
          @Override
          public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
            throws DiffRequestProducerException, ProcessCanceledException {
            try {
              TextFilePatch patch = preloader.getPatch(shelvedChange, commitContext);
              PatchDiffRequest patchDiffRequest =
                new PatchDiffRequest(createAppliedTextPatch(patch), getName(), VcsBundle.message("patch.apply.conflict.patch"));
              DiffUtil.addNotification(createNotification("Cannot find local file for '" + chooseNotNull(beforePath, afterPath) + "'"),
                                       patchDiffRequest);
              return patchDiffRequest;
            }
            catch (VcsException e) {
              throw new DiffRequestProducerException("Can't show diff for '" + getName() + "'", e);
            }
          }
        });
        continue;
      }

      diffRequestProducers.add(new MyDiffRequestProducer(shelvedChange, filePath) {
        @NotNull
        @Override
        public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
          throws DiffRequestProducerException, ProcessCanceledException {
          if (!isNewFile && file.getFileType() == UnknownFileType.INSTANCE) {
            return new UnknownFileTypeDiffRequest(file, getName());
          }
          if (isNewFile) return createDiffRequest(project, shelvedChange.getChange(project), getName(), context, indicator);

          final TextFilePatch patch;
          try {
            patch = preloader.getPatch(shelvedChange, commitContext);
          }
          catch (VcsException e) {
            throw new DiffRequestProducerException("Can't show diff for '" + getName() + "'", e);
          }

          if (patch.isDeletedFile()) {
            return createDiffRequestForDeleted(patch);
          }
          return createDiffRequestForModified(patch, commitContext, context, indicator);
        }

        @NotNull
        private DiffRequest createDiffRequestForDeleted(@NotNull TextFilePatch patch) {
          assert file != null;
          DiffContentFactory contentFactory = DiffContentFactory.getInstance();
          DiffContent leftContent = withLocal
                                    ? contentFactory.create(project, file)
                                    : contentFactory.create(project, patch.getSingleHunkPatchText(), file);
          return new SimpleDiffRequest(getName(), leftContent,
                                       contentFactory.createEmpty(),
                                       withLocal ? CURRENT_VERSION : SHELVED_VERSION, null);
        }

        @NotNull
        private DiffRequest createDiffRequestForModified(@NotNull TextFilePatch patch,
                                                         @NotNull CommitContext commitContext,
                                                         @NotNull UserDataHolder context,
                                                         @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
          assert file != null;
          CharSequence baseContents = Extensions.findExtension(PatchEP.EP_NAME, project, BaseRevisionTextPatchEP.class)
            .provideContent(chooseNotNull(patch.getAfterName(), patch.getBeforeName()), commitContext);
          ApplyPatchForBaseRevisionTexts texts =
            ApplyPatchForBaseRevisionTexts.create(project, file, patchContext.getPathBeforeRename(file), patch, baseContents);
          //found base
          if (texts.isBaseRevisionLoaded()) {
            assert !texts.isAppliedSomehow();

            //normal diff
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            DiffContent leftContent = withLocal
                                      ? contentFactory.create(project, file)
                                      : contentFactory.create(project, assertNotNull(texts.getBase()), file);
            return new SimpleDiffRequest(getName(), leftContent, contentFactory.create(project, texts.getPatched(), file),
                                         withLocal ? CURRENT_VERSION : BASE_VERSION, SHELVED_VERSION);
          }
          else {
            DiffRequest diffRequest = shelvedChange.isConflictingChange(project)
                                      ? createConflictDiffRequest(project, file, patch, SHELVED_VERSION, texts, getName())
                                      : createDiffRequest(project, shelvedChange.getChange(project), getName(), context, indicator);
            if (!withLocal) {
              DiffUtil.addNotification(createNotification(DIFF_WITH_BASE_ERROR + " Showing difference with local version"), diffRequest);
            }
            return diffRequest;
          }
        }
      });
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

  static class PatchesPreloader {
    private final Project myProject;
    private final SoftHardCacheMap<String, PatchInfo> myFilePatchesMap = new SoftHardCacheMap<>(5, 5);
    private final ReadWriteLock myLock = new ReentrantReadWriteLock(true);

    PatchesPreloader(final Project project) {
      myProject = project;
    }

    @NotNull
    @CalledInBackground
    public TextFilePatch getPatch(final ShelvedChange shelvedChange, @Nullable CommitContext commitContext) throws VcsException {
      String patchPath = shelvedChange.getPatchPath();
      if (getInfoFromCache(patchPath) == null || isPatchFileChanged(patchPath)) {
        readFilePatchAndUpdateCaches(patchPath, commitContext);
      }
      PatchInfo patchInfo = getInfoFromCache(patchPath);
      if (patchInfo != null) {
        for (TextFilePatch textFilePatch : patchInfo.myTextFilePatches) {
          if (shelvedChange.getBeforePath().equals(textFilePatch.getBeforeName())) {
            return textFilePatch;
          }
        }
      }
      throw new VcsException("Can not find patch for " + shelvedChange.getBeforePath() + " in patch file.");
    }

    private PatchInfo getInfoFromCache(@NotNull String patchPath) {
      try {
        myLock.readLock().lock();
        return myFilePatchesMap.get(patchPath);
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    private void readFilePatchAndUpdateCaches(@NotNull String patchPath, @Nullable CommitContext commitContext) throws VcsException {
      try {
        myLock.writeLock().lock();
        myFilePatchesMap.put(patchPath, new PatchInfo(ShelveChangesManager.loadPatches(myProject, patchPath, commitContext),
                                                      new File(patchPath).lastModified()));
      }
      catch (IOException | PatchSyntaxException e) {
        throw new VcsException(e);
      }
      finally {
        myLock.writeLock().unlock();
      }
    }

    public boolean isPatchFileChanged(@NotNull String patchPath) {
      PatchInfo patchInfo = getInfoFromCache(patchPath);
      long lastModified = new File(patchPath).lastModified();
      return patchInfo != null && lastModified != patchInfo.myLoadedTimeStamp;
    }

    private static class PatchInfo {

      private final long myLoadedTimeStamp;
      @NotNull private final List<TextFilePatch> myTextFilePatches;

      public PatchInfo(@NotNull List<TextFilePatch> patches, long loadedTimeStamp) {
        myTextFilePatches = patches;
        myLoadedTimeStamp = loadedTimeStamp;
      }
    }
  }

  private static abstract class MyDiffRequestProducer implements ChangeDiffRequestChain.Producer {
    @Nullable private final ShelvedChange myTextChange;
    @Nullable private final ShelvedBinaryFile myBinaryChange;
    @NotNull private final FilePath myFilePath;

    public MyDiffRequestProducer(@NotNull ShelvedChange textChange, @NotNull FilePath filePath) {
      myBinaryChange = null;
      myTextChange = textChange;
      myFilePath = filePath;
    }

    public MyDiffRequestProducer(@NotNull ShelvedBinaryFile binaryChange, @NotNull FilePath filePath) {
      myBinaryChange = binaryChange;
      myTextChange = null;
      myFilePath = filePath;
    }

    @Nullable
    public ShelvedChange getTextChange() {
      return myTextChange;
    }

    @Nullable
    public ShelvedBinaryFile getBinaryChange() {
      return myBinaryChange;
    }

    @NotNull
    @Override
    public String getName() {
      return FileUtil.toSystemDependentName(getFilePath().getPath());
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      if (myTextChange != null) {
        return myTextChange.getFileStatus();
      }
      else {
        assert myBinaryChange != null;
        return myBinaryChange.getFileStatus();
      }
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFilePath;
    }
  }
}
