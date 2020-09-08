// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.browser.BulkMovesOnlyChangesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitBulkMovesOnlyChangesFilter implements BulkMovesOnlyChangesFilter {
  private static final Logger LOG = Logger.getInstance(GitBulkMovesOnlyChangesFilter.class);

  @Override
  public @Nullable Collection<Change> filter(@Nullable Project project, @NotNull List<Change> changes) {
    try {
      if (project == null) return null;

      MultiMap<Pair<GitRevisionNumber, VirtualFile>, FilePath> revisionMap = MultiMap.createSet();
      for (Change change : changes) {
        if (!putRevision(project, revisionMap, change.getBeforeRevision())) return null;
        if (!putRevision(project, revisionMap, change.getAfterRevision())) return null;
      }

      ProgressManager.checkCanceled();
      Map<Pair<GitRevisionNumber, FilePath>, String> blobs = new HashMap<>();
      for (Pair<GitRevisionNumber, VirtualFile> key : revisionMap.keySet()) {
        GitRevisionNumber revision = key.getFirst();
        VirtualFile root = key.getSecond();
        Collection<FilePath> filePaths = revisionMap.get(key);

        GitRepository repository = GitUtil.getRepositoryForRoot(project, root);
        List<GitIndexUtil.StagedFileOrDirectory> treeEntries = GitIndexUtil.listTree(repository, filePaths, revision);
        if (treeEntries.size() != filePaths.size()) {
          throw new VcsException(GitBundle.message("unexpected.tree.entries.error", revision));
        }

        for (GitIndexUtil.StagedFileOrDirectory entry : treeEntries) {
          if (entry instanceof GitIndexUtil.StagedFile) {
            String hash = ((GitIndexUtil.StagedFile)entry).getBlobHash();
            blobs.put(Pair.create(revision, entry.getPath()), hash);
          }
          else {
            throw new VcsException(GitBundle.message("unexpected.tree.object.error", entry));
          }
        }
      }

      ProgressManager.checkCanceled();
      List<Change> filtered = new ArrayList<>();
      for (Change change : changes) {
        if (acceptChange(blobs, change)) {
          filtered.add(change);
        }
      }
      return filtered;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static boolean putRevision(@NotNull Project project,
                                     @NotNull MultiMap<Pair<GitRevisionNumber, VirtualFile>, FilePath> map,
                                     @Nullable ContentRevision revision) {
    if (revision == null) return true;
    if (!(revision instanceof GitContentRevision)) return false;

    FilePath filePath = revision.getFile();
    GitRevisionNumber number = (GitRevisionNumber)revision.getRevisionNumber();

    VcsRoot vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(filePath);
    if (vcsRoot == null || vcsRoot.getVcs() == null) return false;
    if (!GitVcs.getKey().equals(vcsRoot.getVcs().getKeyInstanceMethod())) return false;

    map.putValue(Pair.create(number, vcsRoot.getPath()), filePath);
    return true;
  }

  private static boolean acceptChange(@NotNull Map<Pair<GitRevisionNumber, FilePath>, String> blobs,
                                      @NotNull Change change) throws VcsException {
    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();
    if (bRev == null || aRev == null) return true;
    return !Objects.equals(findBlob(blobs, bRev), findBlob(blobs, aRev));
  }

  @NotNull
  private static String findBlob(@NotNull Map<Pair<GitRevisionNumber, FilePath>, String> blobs,
                                 @NotNull ContentRevision revision) throws VcsException {
    FilePath filePath = revision.getFile();
    GitRevisionNumber number = (GitRevisionNumber)revision.getRevisionNumber();
    String blob = blobs.get(Pair.create(number, filePath));
    if (blob == null) throw new VcsException(GitBundle.message("blob.not.found", number, filePath));
    return blob;
  }
}
