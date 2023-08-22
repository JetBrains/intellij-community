// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class MatchPatchPaths {
  private static final int BIG_FILE_BOUND = 100000;
  private final Project myProject;
  private final VirtualFile myBaseDir;
  private boolean myUseProjectRootAsPredefinedBase;

  public MatchPatchPaths(@NotNull Project project) {
    myProject = project;
    myBaseDir = myProject.getBaseDir();
  }

  public List<AbstractFilePatchInProgress<?>> execute(@NotNull List<? extends FilePatch> list) {
    return execute(list, false);
  }

  /**
   * Find the best matched bases for file patches; e.g. Unshelve has to use project dir as best base by default,
   * while Apply patch should process through context, because it may have been created outside IDE for a certain vcs root
   *
   * @param useProjectRootAsPredefinedBase if true then we use project dir as default base despite context matching
   */
  public List<AbstractFilePatchInProgress<?>> execute(@NotNull List<? extends FilePatch> list, boolean useProjectRootAsPredefinedBase) {
    final PatchBaseDirectoryDetector directoryDetector = PatchBaseDirectoryDetector.getInstance(myProject);

    myUseProjectRootAsPredefinedBase = useProjectRootAsPredefinedBase;
    List<PatchAndVariants> candidates = new ArrayList<>(list.size());
    List<FilePatch> newOrWithoutMatches = new ArrayList<>();
    findCandidates(list, directoryDetector, candidates, newOrWithoutMatches);

    MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result = new MultiMap<>();
    // process exact matches: if one, leave and extract. if several - leave only them
    filterExactMatches(candidates, result);

    // partially check by context
    selectByContextOrByStrip(candidates, result); // for text only
    // created or no variants
    workWithNotExisting(directoryDetector, newOrWithoutMatches, result);
    return new ArrayList<>(result.values());
  }

  private void workWithNotExisting(@NotNull PatchBaseDirectoryDetector directoryDetector,
                                   @NotNull List<? extends FilePatch> newOrWithoutMatches,
                                   @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result) {
    for (FilePatch patch : newOrWithoutMatches) {
      String afterName = patch.getAfterName();
      String[] strings = getPathParts(afterName);
      FileBaseMatch best = null;
      boolean bestIsUnique = true;
      for (int i = strings.length - 2; i >= 0; --i) {
        final String name = strings[i];
        final Collection<VirtualFile> files = findFilesFromIndex(directoryDetector, name);
        if (!files.isEmpty()) {
          // check all candidates
          for (VirtualFile file : files) {
            FileBaseMatch match = compareNamesImpl(strings, file, i);
            if (match != null && match.score < i) {
              if (best == null || isBetterMatch(match, best)) {
                best = match;
                bestIsUnique = true;
              }
              else if (!match.file.equals(best.file) && !isBetterMatch(best, match)) {
                bestIsUnique = false;
              }
            }
          }
        }
      }
      if (best != null && bestIsUnique) {
        AbstractFilePatchInProgress<?> patchInProgress = createPatchInProgress(patch, best.file);
        if (patchInProgress == null) {
          break;
        }
        processStipUp(patchInProgress, best.score);
        result.putValue(best.file, patchInProgress);
      }
      else {
        AbstractFilePatchInProgress<?> patchInProgress = createPatchInProgress(patch, myBaseDir);
        if (patchInProgress == null) {
          break;
        }
        result.putValue(myBaseDir, patchInProgress);
      }
    }
  }

  private boolean isBetterMatch(@NotNull FileBaseMatch match, @NotNull FileBaseMatch best) {
    return match.score < best.score ||
           match.score == best.score && myBaseDir.equals(match.file);
  }

  private static void selectByContextOrByStrip(@NotNull List<PatchAndVariants> candidates,
                                               @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result) {
    for (final PatchAndVariants candidate : candidates) {
      candidate.findAndAddBestVariant(result);
    }
  }

  private static void filterExactMatches(@NotNull List<PatchAndVariants> candidates,
                                         @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result) {
    for (Iterator<PatchAndVariants> iterator = candidates.iterator(); iterator.hasNext(); ) {
      final PatchAndVariants candidate = iterator.next();
      if (candidate.getVariants().size() == 1) {
        final AbstractFilePatchInProgress oneCandidate = candidate.getVariants().get(0);
        result.putValue(oneCandidate.getBase(), oneCandidate);
        iterator.remove();
      }
      else {
        final List<AbstractFilePatchInProgress> exact = new ArrayList<>(candidate.getVariants().size());
        for (AbstractFilePatchInProgress patch : candidate.getVariants()) {
          if (patch.getCurrentStrip() == 0) {
            exact.add(patch);
          }
        }
        if (exact.size() == 1) {
          AbstractFilePatchInProgress<?> patchInProgress = exact.get(0);
          putSelected(result, candidate.getVariants(), patchInProgress);
          iterator.remove();
        }
        else if (!exact.isEmpty()) {
          candidate.getVariants().retainAll(exact);
        }
      }
    }
  }

  private void findCandidates(@NotNull List<? extends FilePatch> list,
                              @NotNull final PatchBaseDirectoryDetector directoryDetector,
                              @NotNull List<? super PatchAndVariants> candidates, @NotNull List<? super FilePatch> newOrWithoutMatches) {
    for (final FilePatch patch : list) {
      final String fileName = patch.getBeforeFileName();
      if (patch.isNewFile() || (patch.getBeforeName() == null)) {
        newOrWithoutMatches.add(patch);
        continue;
      }
      final Collection<VirtualFile> files = new ArrayList<>(findFilesFromIndex(directoryDetector, fileName));
      // for directories outside the project scope but under version control
      if (patch.getBeforeName() != null && patch.getBeforeName().startsWith("..")) {
        final VirtualFile relativeFile = VfsUtil.findRelativeFile(myBaseDir, getPathParts(patch.getBeforeName()));
        if (relativeFile != null) {
          files.add(relativeFile);
        }
      }
      if (files.isEmpty()) {
        newOrWithoutMatches.add(patch);
      }
      else {
        //files order is not defined, so get the best variant depends on it, too
        List<AbstractFilePatchInProgress<?>> variants = ContainerUtil.mapNotNull(files, file -> processMatch(patch, file));
        if (variants.isEmpty()) {
          newOrWithoutMatches.add(patch); // just to be sure
        }
        else {
          candidates.add(new PatchAndVariants(variants));
        }
      }
    }
  }

  private Collection<VirtualFile> findFilesFromIndex(@NotNull final PatchBaseDirectoryDetector directoryDetector,
                                                     @NotNull final String fileName) {
    Collection<VirtualFile> files = ReadAction.compute(() -> directoryDetector.findFiles(fileName));
    final File shelfResourcesDirectory = ShelveChangesManager.getInstance(myProject).getShelfResourcesDirectory();
    return ContainerUtil.filter(files, file -> !FileUtil.isAncestor(shelfResourcesDirectory, VfsUtilCore.virtualToIoFile(file), false));
  }

  private static void putSelected(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result,
                                  @NotNull List<? extends AbstractFilePatchInProgress<?>> variants,
                                  @NotNull AbstractFilePatchInProgress<?> patchInProgress) {
    patchInProgress.setAutoBases(ContainerUtil.mapNotNull(variants, AbstractFilePatchInProgress::getBase));
    result.putValue(patchInProgress.getBase(), patchInProgress);
  }

  private static int getMatchingLines(final AbstractFilePatchInProgress<TextFilePatch> patch) {
    final VirtualFile base = patch.getCurrentBase();
    if (base == null) return -1;
    String text;
    try {
      if (base.getLength() > BIG_FILE_BOUND) {
        // partially
        text = VfsUtilCore.loadText(base, BIG_FILE_BOUND);
      }
      else {
        text = VfsUtilCore.loadText(base);
      }
    }
    catch (IOException e) {
      return 0;
    }
    return new GenericPatchApplier(text, patch.getPatch().getHunks()).weightContextMatch(100, 5);
  }

  private final class PatchAndVariants {
    @NotNull private final List<AbstractFilePatchInProgress<?>> myVariants;

    private PatchAndVariants(@NotNull List<AbstractFilePatchInProgress<?>> variants) {
      myVariants = variants;
    }

    @NotNull
    public List<AbstractFilePatchInProgress<?>> getVariants() {
      return myVariants;
    }

    public void findAndAddBestVariant(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> result) {
      AbstractFilePatchInProgress first = ContainerUtil.getFirstItem(myVariants);
      if (first == null) return;

      AbstractFilePatchInProgress best = null;
      if (first instanceof TextFilePatchInProgress) {
        if (myUseProjectRootAsPredefinedBase) {
          best = findBestByBaseDir();
        }
        if (best == null) {
          best = findBestByText();
        }
      }
      else {
        best = findBestByBaseDir();
        if (best == null) {
          best = findBestByStrip();
        }
      }

      if (best != null) {
        putSelected(result, myVariants, best);
      }
    }

    @Nullable
    private AbstractFilePatchInProgress findBestByBaseDir() {
      for (AbstractFilePatchInProgress variant : myVariants) {
        if (variantMatchedToProjectDir(variant)) {
          return variant;
        }
      }
      return null;
    }

    @Nullable
    private AbstractFilePatchInProgress findBestByText() {
      AbstractFilePatchInProgress best = null;
      int bestLines = Integer.MIN_VALUE;
      boolean bestIsUnique = true;

      AbstractFilePatchInProgress baseDirVariant = null;

      for (AbstractFilePatchInProgress variant : myVariants) {
        TextFilePatchInProgress current = (TextFilePatchInProgress)variant;
        final int currentLines = getMatchingLines(current);
        if (best == null ||
            isBetterMatch(current, currentLines,
                          best, bestLines)) {
          bestLines = currentLines;
          best = current;
          bestIsUnique = true;
        }
        else if (!isBetterMatch(best, bestLines,
                                current, currentLines)) {
          bestIsUnique = false;
        }

        if (baseDirVariant == null && myBaseDir.equals(current.getBase())) {
          baseDirVariant = current;
        }
      }

      if (!bestIsUnique && baseDirVariant != null) {
        return baseDirVariant;
      }

      return best;
    }

    @Nullable
    private AbstractFilePatchInProgress findBestByStrip() {
      AbstractFilePatchInProgress best = null;
      int bestStrip = Integer.MAX_VALUE;

      for (AbstractFilePatchInProgress current : myVariants) {
        int currentStrip = current.getCurrentStrip();
        if (best == null ||
            currentStrip < bestStrip) {
          best = current;
          bestStrip = currentStrip;
        }
      }
      return best;
    }
  }

  private boolean isBetterMatch(@NotNull AbstractFilePatchInProgress<?> match, int matchLines,
                                @NotNull AbstractFilePatchInProgress<?> best, int bestLines) {
    return matchLines > bestLines ||
           matchLines == bestLines && myBaseDir.equals(match.getBase());
  }

  private boolean variantMatchedToProjectDir(@NotNull AbstractFilePatchInProgress variant) {
    if (variant.getCurrentStrip() == 0) {
      return myBaseDir.equals(variant.getBase());
    }

    int upDirCount = 0;
    VirtualFile base = myBaseDir;

    for (String part : getPathParts(variant.getOriginalBeforePath())) {
      if (!part.equals("..")) {
        break;
      }

      upDirCount++;
      if (base != null) base = base.getParent();
    }

    return upDirCount == variant.getCurrentStrip() &&
           base != null && base.equals(variant.getBase());
  }

  @Nullable
  private static FileBaseMatch compareNames(final String beforeName, final VirtualFile file) {
    if (beforeName == null) {
      return null;
    }
    String[] parts = getPathParts(beforeName);
    return compareNamesImpl(parts, file.getParent(), parts.length - 2);
  }

  private static String @NotNull [] getPathParts(@Nullable String relativePath) {
    return relativePath == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : relativePath.replace('\\', '/').split("/");
  }

  @Nullable
  private static FileBaseMatch compareNamesImpl(String[] parts, VirtualFile parent, int idx) {
    while (parent != null && idx >= 0 && parent.getName().equals(parts[idx])) {
      parent = parent.getParent();
      --idx;
    }
    return parent != null ? new FileBaseMatch(parent, idx + 1) : null;
  }

  private static @Nullable AbstractFilePatchInProgress<?> processMatch(@NotNull FilePatch patch, @NotNull VirtualFile file) {
    String beforeName = patch.getBeforeName();
    FileBaseMatch match = compareNames(beforeName, file);
    if (match == null) {
      return null;
    }
    AbstractFilePatchInProgress<?> result = createPatchInProgress(patch, match.file);
    if (result != null) {
      processStipUp(result, match.score);
    }
    return result;
  }

  private static @Nullable AbstractFilePatchInProgress<?> createPatchInProgress(@NotNull FilePatch patch, @NotNull VirtualFile dir) {
    if (patch instanceof TextFilePatch) {
      return new TextFilePatchInProgress((TextFilePatch)patch, null, dir);
    }
    if (patch instanceof ShelvedBinaryFilePatch) {
      return new ShelvedBinaryFilePatchInProgress((ShelvedBinaryFilePatch)patch, null, dir);
    }
    if (patch instanceof BinaryFilePatch) {
      return new BinaryFilePatchInProgress((BinaryFilePatch)patch, null, dir);
    }
    return null;
  }

  private static void processStipUp(@NotNull Strippable patchInProgress, int num) {
    for (int i = 0; i < num; i++) {
      patchInProgress.up();
    }
  }

  private static final class FileBaseMatch {
    @NotNull public final VirtualFile file;
    public final int score;

    FileBaseMatch(@NotNull VirtualFile file, int score) {
      this.file = file;
      this.score = score;
    }
  }
}
