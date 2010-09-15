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
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyTextFilePatch;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.patch.MergedDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.patch.PatchMergeRequestFactory;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    ShelvedChangeList[] changeLists = ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY.getData(dc);
    if (changeLists == null) {
      changeLists = ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY.getData(dc);
    }

    // selected changes inside lists
    List<ShelvedChange> shelvedChanges = ShelvedChangesViewManager.SHELVED_CHANGE_KEY.getData(dc);

    if (changeLists == null) return;

    final List<ShelvedChange> changesFromFirstList = changeLists[0].getChanges();

    Collections.sort(changesFromFirstList, new MyComparator(project));

    int toSelectIdx = 0;
    final ArrayList<DiffRequestPresentable> diffRequestPresentables = new ArrayList<DiffRequestPresentable>();
    final ApplyPatchContext context = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    final PatchesPreloader preloader = new PatchesPreloader();

    final List<String> missing = new LinkedList<String>();
    for (final ShelvedChange shelvedChange : changesFromFirstList) {
      final Change change = shelvedChange.getChange(project);
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
          @Override
          protected DiffRequestPresentable init() {
            if (isConflictingChange(change)) {
              TextFilePatch patch = preloader.getPatch(shelvedChange);
              if (patch == null) return null;

              final FilePath pathBeforeRename = context.getPathBeforeRename(f);

              final ApplyPatchForBaseRevisionTexts threeTexts = ApplyPatchForBaseRevisionTexts.create(project, f, pathBeforeRename, patch);
              if ((threeTexts == null) || (threeTexts.getStatus() == null) || (ApplyPatchStatus.FAILURE.equals(threeTexts.getStatus()))) {
                return null;
              }

              return new MergedDiffRequestPresentable(project, threeTexts, f, "Shelved Version");
            }
            else {
              return new ChangeDiffRequestPresentable(project, change);
            }
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
      VcsBalloonProblemNotifier.showMe(project, "Show Diff: Cannot find base for: " + StringUtil.join(missing, ",\n"), MessageType.WARNING);
    }

    ShowDiffAction.showDiffImpl(project, diffRequestPresentables, toSelectIdx, new ShowDiffUIContext(false));
  }

  private static class PatchesPreloader {
    private final Map<String, List<TextFilePatch>> myFilePatchesMap;

    private PatchesPreloader() {
      myFilePatchesMap = new HashMap<String, List<TextFilePatch>>();
    }

    @Nullable
    public TextFilePatch getPatch(final ShelvedChange shelvedChange) {
      List<TextFilePatch> textFilePatches = myFilePatchesMap.get(shelvedChange.getPatchPath());
      if (textFilePatches == null) {
        try {
          textFilePatches = ShelveChangesManager.loadPatches(shelvedChange.getPatchPath());
        }
        catch (IOException e) {
          return null;
        }
        catch (PatchSyntaxException e) {
          return null;
        }
        myFilePatchesMap.put(shelvedChange.getPatchPath(), textFilePatches);
      }
      for (TextFilePatch textFilePatch : textFilePatches) {
        if (shelvedChange.getBeforePath().equals(textFilePatch.getBeforeName())) {
          return textFilePatch;
        }
      }
      return null;
    }
  }

  private final static class MyComparator implements Comparator<ShelvedChange> {
    private final Project myProject;

    public MyComparator(Project project) {
      myProject = project;
    }

    public int compare(final ShelvedChange o1, final ShelvedChange o2) {
      return ChangesComparator.getInstance().compare(o1.getChange(myProject), o2.getChange(myProject));
    }
  }

  private static boolean isConflictingChange(final Change change) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision == null) return false;
    try {
      afterRevision.getContent();
    }
    catch(VcsException e) {
      if (e.getCause() instanceof ApplyPatchException) {
        return true;
      }
    }
    return false;
  }

  public void update(final AnActionEvent e) {
    ActionManager.getInstance().getAction("ChangesView.Diff").update(e);
  }

  private static class ShelvedChangeDiffRequestFactory implements PatchMergeRequestFactory {
    public static final ShelvedChangeDiffRequestFactory INSTANCE = new ShelvedChangeDiffRequestFactory();

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request = DiffRequestFactory.getInstance().create3WayDiffRequest(leftText, rightText, originalContent,
                                                                                    project,
                                                                                    null);
      request.setVersionTitles(new String[] {
        "Current Version",
        "Base Version",
        "Shelved Version"
      });
      request.setWindowTitle("Shelved Change Conflict for" + file.getPresentableUrl());
      return request;
    }
  }
}
