/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyTextFilePatch;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.MergedDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class DiffShelvedChangesAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    showShelvedChangesDiff(e.getDataContext());
  }

  public static void showShelvedChangesDiff(final DataContext dc) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    ShelvedChangeList[] changeLists = ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY.getData(dc);
    if (changeLists == null) {
      changeLists = ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY.getData(dc);
    }

    if (changeLists == null) return;
    final List<ShelvedChange> changesFromFirstList = changeLists[0].getChanges(project);

    final ArrayList<DiffRequestPresentable> diffRequestPresentables = new ArrayList<DiffRequestPresentable>();
    final ApplyPatchContext context = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    final PatchesPreloader preloader = new PatchesPreloader(project);

    final List<String> missing = new LinkedList<String>();
    processTextChanges(project, changesFromFirstList, diffRequestPresentables, context, preloader, missing);
    final List<ShelvedBinaryFile> files = changeLists[0].getBinaryFiles();
    processBinaryFiles(project, files, diffRequestPresentables);
    if (! missing.isEmpty()) {
      // 7-8
      VcsBalloonProblemNotifier.showOverChangesView(project, "Show Diff: Cannot find base for: " + StringUtil.join(missing, ",\n"), MessageType.WARNING);
    }

    Collections.sort(diffRequestPresentables, ChangeDiffRequestComparator.getInstance());

    // selected changes inside lists
    final Set<String> selectedPaths = new HashSet<String>();
    final List<ShelvedChange> shelvedChanges = ShelvedChangesViewManager.SHELVED_CHANGE_KEY.getData(dc);
    final List<ShelvedBinaryFile> binaryFiles = ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY.getData(dc);
    for (ShelvedChange change : shelvedChanges) {
      selectedPaths.add(FilePathsHelper.convertPath(ChangesUtil.getFilePath(change.getChange(project)).getPath()));
    }
    for (ShelvedBinaryFile file : binaryFiles) {
      selectedPaths.add(FilePathsHelper.convertPath(ChangesUtil.getFilePath(file.createChange(project))));
    }
    int idx = 0;
    for (DiffRequestPresentable presentable : diffRequestPresentables) {
      final String path = FilePathsHelper.convertPath(presentable.getPathPresentation());
      if (selectedPaths.contains(path)) {
        break;
      }
      ++ idx;
    }
    idx = idx >= diffRequestPresentables.size() ? 0 : idx;
    ShowDiffAction.showDiffImpl(project, diffRequestPresentables, idx, new ShowDiffUIContext(true));
  }

  private static class ChangeDiffRequestComparator implements Comparator<DiffRequestPresentable> {
    private final static ChangeDiffRequestComparator ourInstance = new ChangeDiffRequestComparator();

    public static ChangeDiffRequestComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(DiffRequestPresentable o1, DiffRequestPresentable o2) {
      return FilePathsHelper.convertPath(o1.getPathPresentation()).compareTo(FilePathsHelper.convertPath(o2.getPathPresentation()));
    }
  }

  private static void processBinaryFiles(final Project project,
                                         List<ShelvedBinaryFile> files,
                                         ArrayList<DiffRequestPresentable> diffRequestPresentables) {
    final String base = project.getBaseDir().getPath();
    for (final ShelvedBinaryFile file : files) {
      diffRequestPresentables.add(new DiffRequestPresentableProxy() {
        @NotNull
        @Override
        protected DiffRequestPresentable init() throws VcsException {
          return new ChangeDiffRequestPresentable(project, file.createChange(project));
        }

        @Override
        public String getPathPresentation() {
          final File file1 = new File(base, file.AFTER_PATH == null ? file.BEFORE_PATH : file.AFTER_PATH);
          return FileUtil.toSystemDependentName(file1.getPath());
        }
      });
    }
  }

  private static void processTextChanges(final Project project,
                                         List<ShelvedChange> changesFromFirstList,
                                         ArrayList<DiffRequestPresentable> diffRequestPresentables,
                                         final ApplyPatchContext context,
                                         final PatchesPreloader preloader,
                                         List<String> missing) {
    final String base = project.getBasePath();
    for (final ShelvedChange shelvedChange : changesFromFirstList) {
      final String beforePath = shelvedChange.getBeforePath();
      try {
        final VirtualFile f = ApplyTextFilePatch
          .findPatchTarget(context, beforePath, shelvedChange.getAfterPath(), FileStatus.ADDED.equals(shelvedChange.getFileStatus()));
        if ((! FileStatus.ADDED.equals(shelvedChange.getFileStatus())) && ((f == null) || (! f.exists()))) {
          if (beforePath != null) {
            missing.add(beforePath);
          }
          continue;
        }

        diffRequestPresentables.add(new DiffRequestPresentableProxy() {
          @NotNull
          @Override
          protected DiffRequestPresentable init() throws VcsException {
            if (shelvedChange.isConflictingChange(project)) {
              final CommitContext commitContext = new CommitContext();
              final TextFilePatch patch = preloader.getPatch(shelvedChange, commitContext);
              final FilePath pathBeforeRename = context.getPathBeforeRename(f);
              final String relativePath = patch.getAfterName() == null ? patch.getBeforeName() : patch.getAfterName();
              final Getter<ApplyPatchForBaseRevisionTexts> revisionTextsGetter = new Getter<ApplyPatchForBaseRevisionTexts>() {
                @Override
                public ApplyPatchForBaseRevisionTexts get() {
                  return ApplyPatchForBaseRevisionTexts.create(project, f, pathBeforeRename, patch,
                                                          new Getter<CharSequence>() {
                                                            @Override
                                                            public CharSequence get() {
                                                              final BaseRevisionTextPatchEP
                                                                baseRevisionTextPatchEP = Extensions
                                                                .findExtension(PatchEP.EP_NAME, project, BaseRevisionTextPatchEP.class);
                                                              if (baseRevisionTextPatchEP != null && commitContext != null) {
                                                                return baseRevisionTextPatchEP.provideContent(relativePath, commitContext);
                                                              }
                                                              return null;
                                                            }
                                                          });
                }
              };
              return new MergedDiffRequestPresentable(project, revisionTextsGetter, f, "Shelved Version");
            }
            else {
              final Change change = shelvedChange.getChange(project);
              return new ChangeDiffRequestPresentable(project, change);
            }
          }

          @Override
          public String getPathPresentation() {
            return FileUtil.toSystemDependentName(
              new File(base, shelvedChange.getAfterPath() == null ? shelvedChange.getBeforePath() : shelvedChange.getAfterPath()).getPath());
          }
        });
      }
      catch (IOException e) {
        continue;
      }
    }
  }

  private static class PatchesPreloader {
    private final Map<String, List<TextFilePatch>> myFilePatchesMap;
    private final Project myProject;

    private PatchesPreloader(final Project project) {
      myProject = project;
      myFilePatchesMap = new HashMap<String, List<TextFilePatch>>();
    }

    @NotNull
    public TextFilePatch getPatch(final ShelvedChange shelvedChange, CommitContext commitContext) throws VcsException {
      List<TextFilePatch> textFilePatches = myFilePatchesMap.get(shelvedChange.getPatchPath());
      if (textFilePatches == null) {
        try {
          textFilePatches = ShelveChangesManager.loadPatches(myProject, shelvedChange.getPatchPath(), commitContext);
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
        catch (PatchSyntaxException e) {
          throw new VcsException(e);
        }
        myFilePatchesMap.put(shelvedChange.getPatchPath(), textFilePatches);
      }
      for (TextFilePatch textFilePatch : textFilePatches) {
        if (shelvedChange.getBeforePath().equals(textFilePatch.getBeforeName())) {
          return textFilePatch;
        }
      }
      throw new VcsException("Can not find patch for " + shelvedChange.getBeforePath() + " in patch file.");
    }
  }

  private final static class MyComparator implements Comparator<ShelvedChange> {
    private final Project myProject;

    public MyComparator(Project project) {
      myProject = project;
    }

    public int compare(final ShelvedChange o1, final ShelvedChange o2) {
      return ChangesComparator.getInstance(true).compare(o1.getChange(myProject), o2.getChange(myProject));
    }
  }

  public void update(final AnActionEvent e) {
    ActionManager.getInstance().getAction("ChangesView.Diff").update(e);
  }
}
