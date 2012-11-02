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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.actions.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.MergedDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

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
    final Project project = PlatformDataKeys.PROJECT.getData(dc);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    ShelvedChangeList[] changeLists = ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY.getData(dc);
    if (changeLists == null) {
      changeLists = ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY.getData(dc);
    }

    // selected changes inside lists
    List<ShelvedChange> shelvedChanges = ShelvedChangesViewManager.SHELVED_CHANGE_KEY.getData(dc);

    if (changeLists == null) return;

    final List<ShelvedChange> changesFromFirstList = changeLists[0].getChanges(project);

    Collections.sort(changesFromFirstList, new MyComparator(project));

    int toSelectIdx = 0;
    final ArrayList<DiffRequestPresentable> diffRequestPresentables = new ArrayList<DiffRequestPresentable>();
    final ApplyPatchContext context = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    final PatchesPreloader preloader = new PatchesPreloader(project);

    final List<String> missing = new LinkedList<String>();
    for (final ShelvedChange shelvedChange : changesFromFirstList) {
      final String beforePath = shelvedChange.getBeforePath();
      try {
        final VirtualFile f = ApplyTextFilePatch.findPatchTarget(context, beforePath, shelvedChange.getAfterPath(), FileStatus.ADDED.equals(shelvedChange.getFileStatus()));
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
                                                                baseRevisionTextPatchEP = Extensions.findExtension(PatchEP.EP_NAME, project, BaseRevisionTextPatchEP.class);
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
            return shelvedChange.getAfterPath() == null ? shelvedChange.getBeforePath() : shelvedChange.getAfterPath();
          }
        });
      }
      catch (IOException e) {
        continue;
      }
      if ((shelvedChanges != null) && shelvedChanges.contains(shelvedChange)) {
        // just added
        toSelectIdx = diffRequestPresentables.size() - 1;
      }
    }
    if (! missing.isEmpty()) {
      // 7-8
      VcsBalloonProblemNotifier.showOverChangesView(project, "Show Diff: Cannot find base for: " + StringUtil.join(missing, ",\n"), MessageType.WARNING);
    }

    ShowDiffAction.showDiffImpl(project, diffRequestPresentables, toSelectIdx, new ShowDiffUIContext(true));
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
