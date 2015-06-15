/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/25/12
 * Time: 5:51 PM
 */
public class MatchPatchPaths {
  private final int ourBigFileBound = 100000;
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public MatchPatchPaths(Project project) {
    myProject = project;
    myBaseDir = myProject.getBaseDir();
  }

  public List<FilePatchInProgress> execute(final List<TextFilePatch> list) {
    final PatchBaseDirectoryDetector directoryDetector = PatchBaseDirectoryDetector.getInstance(myProject);

    final List<PatchAndVariants> candidates = new ArrayList<PatchAndVariants>(list.size());
    final List<TextFilePatch> newOrWithoutMatches = new ArrayList<TextFilePatch>();
    findCandidates(list, directoryDetector, candidates, newOrWithoutMatches);

    final MultiMap<VirtualFile, FilePatchInProgress> result = new MultiMap<VirtualFile, FilePatchInProgress>();
    // process exact matches: if one, leave and extract. if several - leave only them
    filterExactMatches(candidates, result);

    // partially check by context
    selectByContext(candidates, result);
    // created or no variants
    workWithNotExisting(directoryDetector, newOrWithoutMatches, result);
    return new ArrayList<FilePatchInProgress>(result.values());
  }

  private void workWithNotExisting(PatchBaseDirectoryDetector directoryDetector,
                                   List<TextFilePatch> newOrWithoutMatches,
                                   MultiMap<VirtualFile, FilePatchInProgress> result) {
    for (TextFilePatch patch : newOrWithoutMatches) {
      final String[] strings = patch.getAfterName().replace('\\', '/').split("/");
      Pair<VirtualFile, Integer> best = null;
      for (int i = strings.length - 2; i >= 0; -- i) {
        final String name = strings[i];
        final Collection<VirtualFile> files = findFilesFromIndex(directoryDetector, name);
        if (! files.isEmpty()) {
          // check all candidates
          for (VirtualFile file : files) {
            Pair<VirtualFile, Integer> pair = compareNamesImpl(strings, file, i);
            if (pair != null && pair.getSecond() < i) {
              if (best == null || pair.getSecond() < best.getSecond()) {
                best = pair;
              }
            }
          }
        }
      }
      if (best != null) {
        final FilePatchInProgress patchInProgress = new FilePatchInProgress(patch, null, myBaseDir);
        patchInProgress.setNewBase(best.getFirst());
        int numDown = best.getSecond();
        for (int i = 0; i < numDown; i++) {
          patchInProgress.up();
        }
        result.putValue(best.getFirst(), patchInProgress);
      } else {
        final FilePatchInProgress patchInProgress = new FilePatchInProgress(patch, null, myBaseDir);
        result.putValue(myBaseDir, patchInProgress);
      }
    }
  }

  private void selectByContext(List<PatchAndVariants> candidates, MultiMap<VirtualFile, FilePatchInProgress> result) {
    for (Iterator<PatchAndVariants> iterator = candidates.iterator(); iterator.hasNext(); ) {
      final PatchAndVariants candidate = iterator.next();
      int maxLines = -100;
      FilePatchInProgress best = null;
      for (FilePatchInProgress variant : candidate.getVariants()) {
        final int lines = getMatchingLines(variant);
        if (lines > maxLines) {
          maxLines = lines;
          best = variant;
        }
      }
      putSelected(result, candidate.getVariants(), best);
    }
  }

  private void filterExactMatches(List<PatchAndVariants> candidates, MultiMap<VirtualFile, FilePatchInProgress> result) {
    for (Iterator<PatchAndVariants> iterator = candidates.iterator(); iterator.hasNext(); ) {
      final PatchAndVariants candidate = iterator.next();
      if (candidate.getVariants().size() == 1) {
        final FilePatchInProgress oneCandidate = candidate.getVariants().get(0);
        result.putValue(oneCandidate.getBase(), oneCandidate);
        iterator.remove();
      } else {
        final List<FilePatchInProgress> exact = new ArrayList<FilePatchInProgress>(candidate.getVariants().size());
        for (FilePatchInProgress patch : candidate.getVariants()) {
          if (patch.getCurrentStrip() == 0) {
            exact.add(patch);
          }
        }
        if (exact.size() == 1) {
          final FilePatchInProgress patchInProgress = exact.get(0);
          putSelected(result, candidate.getVariants(), patchInProgress);
          iterator.remove();
        } else if (! exact.isEmpty()) {
          candidate.getVariants().retainAll(exact);
        }
      }
    }
  }

  private void findCandidates(List<TextFilePatch> list,
                              final PatchBaseDirectoryDetector directoryDetector,
                              List<PatchAndVariants> candidates, List<TextFilePatch> newOrWithoutMatches) {
    for (final TextFilePatch patch : list) {
      final String fileName = patch.getBeforeFileName();
      if (patch.isNewFile() || (patch.getBeforeName() == null)) {
        newOrWithoutMatches.add(patch);
        continue;
      }
      final Collection<VirtualFile> files = findFilesFromIndex(directoryDetector, fileName);
      // for directories outside the project scope but under version control
      if (patch.getBeforeName() != null && patch.getBeforeName().startsWith("..")) {
        final VirtualFile relativeFile = VfsUtil.findRelativeFile(myBaseDir, patch.getBeforeName().replace('\\', '/').split("/"));
        if (relativeFile != null) {
          files.add(relativeFile);
        }
      }
      if (files.isEmpty()) {
        newOrWithoutMatches.add(patch);
      } else {
        final List<FilePatchInProgress> variants = ObjectsConvertor.convert(files, new Convertor<VirtualFile, FilePatchInProgress>() {
          @Override
          public FilePatchInProgress convert(VirtualFile o) {
            return processMatch(patch, o);
          }
        }, ObjectsConvertor.NOT_NULL);
        if (variants.isEmpty()) {
          newOrWithoutMatches.add(patch); // just to be sure
        } else {
          candidates.add(new PatchAndVariants(patch, variants));
        }
      }
    }
  }

  private static Collection<VirtualFile> findFilesFromIndex(@NotNull final PatchBaseDirectoryDetector directoryDetector,
                                                            @NotNull final String fileName) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<VirtualFile>>() {
      public Collection<VirtualFile> compute() {
        return directoryDetector.findFiles(fileName);
      }
    });
  }

  private void putSelected(MultiMap<VirtualFile, FilePatchInProgress> result,
                           final List<FilePatchInProgress> variants,
                           FilePatchInProgress patchInProgress) {
    patchInProgress.setAutoBases(ObjectsConvertor.convert(variants, new Convertor<FilePatchInProgress, VirtualFile>() {
      @Override
      public VirtualFile convert(FilePatchInProgress o) {
        return o.getBase();
      }
    }, ObjectsConvertor.NOT_NULL));
    result.putValue(patchInProgress.getBase(), patchInProgress);
  }

  private int getMatchingLines(final FilePatchInProgress patch) {
    final VirtualFile base = patch.getCurrentBase();
    if (base == null) return -1;
    String text;
    try {
      if (base.getLength() > ourBigFileBound) {
        // partially
        text = VfsUtil.loadText(base, ourBigFileBound);
      } else {
        text = VfsUtil.loadText(base);
      }
    }
    catch (IOException e) {
      return 0;
    }
    return new GenericPatchApplier(text, patch.getPatch().getHunks()).weightContextMatch(100, 5);
  }

  private static class PatchAndVariants {
    private final TextFilePatch myPatch;
    private final List<FilePatchInProgress> myVariants;

    private PatchAndVariants(TextFilePatch patch, List<FilePatchInProgress> variants) {
      myPatch = patch;
      myVariants = variants;
    }

    public TextFilePatch getPatch() {
      return myPatch;
    }

    public List<FilePatchInProgress> getVariants() {
      return myVariants;
    }
  }

  private Pair<VirtualFile, Integer> compareNames(final String beforeName, final VirtualFile file) {
    if (beforeName == null) return null;
    final String[] parts = beforeName.replace('\\', '/').split("/");
    return compareNamesImpl(parts, file.getParent(), parts.length - 2);
  }

  private Pair<VirtualFile, Integer> compareNamesImpl(String[] parts, VirtualFile parent, int idx) {
    VirtualFile previous = parent;
    while ((parent != null) && (idx >= 0)) {
      if (! parent.getName().equals(parts[idx])) {
        return new Pair<VirtualFile, Integer>(parent, idx + 1);
      }
      previous = parent;
      parent = parent.getParent();
      -- idx;
    }
    return new Pair<VirtualFile, Integer>(parent, idx + 1);
  }

  @Nullable
  private FilePatchInProgress processMatch(final TextFilePatch patch, final VirtualFile file) {
    final String beforeName = patch.getBeforeName();
    /*if (beforeName == null) return null;
    final String[] parts = beforeName.replace('\\', '/').split("/");
    VirtualFile parent = file.getParent();
    int idx = parts.length - 2;
    while ((parent != null) && (idx >= 0)) {
      if (! parent.getName().equals(parts[idx])) {
        break;
      }
      parent = parent.getParent();
      -- idx;
    }*/
    final Pair<VirtualFile, Integer> pair = compareNames(beforeName, file);
    if (pair == null) return null;
    final VirtualFile parent = pair.getFirst();
    if (parent != null) {
      final FilePatchInProgress result = new FilePatchInProgress(patch, null, myBaseDir);
      result.setNewBase(parent);
      int numDown = pair.getSecond();
      for (int i = 0; i < numDown; i++) {
        result.up();
      }
      return result;
    }
    return null;
  }
}
