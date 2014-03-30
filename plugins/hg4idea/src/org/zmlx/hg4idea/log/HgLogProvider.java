/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.zmlx.hg4idea.log;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgConfig;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgHistoryUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.text.SimpleDateFormat;
import java.util.*;

public class HgLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(HgLogProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final HgRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  private static final String RECENT_HEAD = "tip";

  public HgLogProvider(@NotNull Project project, @NotNull HgRepositoryManager repositoryManager, @NotNull VcsLogObjectsFactory factory) {
    myProject = project;
    myRepositoryManager = repositoryManager;
    myRefSorter = new HgRefManager();
    myVcsObjectsFactory = factory;
  }

  @NotNull
  @Override
  public List<? extends VcsCommitMetadata> readFirstBlock(@NotNull VirtualFile root,
                                                          boolean ordered, int commitCount) throws VcsException {
    return HgHistoryUtil.loadMetadata(myProject, root, commitCount,
                                      ordered ? Collections.<String>emptyList() : Arrays.asList("-r", "0:tip"));
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<VcsUser> userRegistry) throws VcsException {
    return HgHistoryUtil.readAllHashes(myProject, root, userRegistry, Collections.<String>emptyList());
  }

  @NotNull
  @Override
  public List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    return HgHistoryUtil.readMiniDetails(myProject, root, hashes);
  }

  @NotNull
  @Override
  public List<? extends VcsFullCommitDetails> readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException {
    return HgHistoryUtil.history(myProject, root, -1, HgHistoryUtil.prepareHashes(hashes));
  }

  @NotNull
  @Override
  public Collection<VcsRef> readAllRefs(@NotNull VirtualFile root) throws VcsException {
    myRepositoryManager.waitUntilInitialized();
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return Collections.emptyList();
    }

    repository.update();
    Map<String, Set<Hash>> branches = repository.getBranches();
    Set<String> openedBranchNames = repository.getOpenedBranches();
    Collection<HgNameWithHashInfo> bookmarks = repository.getBookmarks();
    Collection<HgNameWithHashInfo> tags = repository.getTags();
    Collection<HgNameWithHashInfo> localTags = repository.getLocalTags();

    Collection<VcsRef> refs = new ArrayList<VcsRef>(branches.size() + bookmarks.size());

    for (Map.Entry<String, Set<Hash>> entry : branches.entrySet()) {
      String branchName = entry.getKey();
      boolean opened = openedBranchNames.contains(branchName);
      for (Hash hash : entry.getValue()) {
        refs.add(myVcsObjectsFactory.createRef(hash, branchName, opened ? HgRefManager.BRANCH : HgRefManager.CLOSED_BRANCH, root));
      }
    }

    for (HgNameWithHashInfo bookmarkInfo : bookmarks) {
      refs.add(myVcsObjectsFactory.createRef(bookmarkInfo.getHash(), bookmarkInfo.getName(),
                         HgRefManager.BOOKMARK, root));
    }
    String currentRevision = repository.getCurrentRevision();
    if (currentRevision != null) { // null => fresh repository
      refs.add(myVcsObjectsFactory.createRef(myVcsObjectsFactory.createHash(currentRevision), RECENT_HEAD, HgRefManager.HEAD, root));
    }
    for (HgNameWithHashInfo tagInfo : tags) {
      refs.add(myVcsObjectsFactory.createRef(tagInfo.getHash(), tagInfo.getName(), HgRefManager.TAG, root));
    }
    for (HgNameWithHashInfo localTagInfo : localTags) {
      refs.add(myVcsObjectsFactory.createRef(localTagInfo.getHash(), localTagInfo.getName(),
                              HgRefManager.LOCAL_TAG, root));
    }
    return refs;
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @NotNull
  @Override
  public VcsLogRefManager getReferenceManager() {
    return myRefSorter;
  }

  @Override
  public void subscribeToRootRefreshEvents(@NotNull final Collection<VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    myProject.getMessageBus().connect(myProject).subscribe(HgVcs.STATUS_TOPIC, new HgUpdater() {
      @Override
      public void update(Project project, @Nullable VirtualFile root) {
        if (root != null && roots.contains(root)) {
          refresher.refresh(root);
        }
      }
    });
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull final VirtualFile root,
                                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                                       int maxCount) throws VcsException {
    List<String> filterParameters = ContainerUtil.newArrayList();

    // branch filter and user filter may be used several times without delimiter
    if (filterCollection.getBranchFilter() != null) {
      HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null) {
        LOG.error("Repository not found for root " + root);
        return Collections.emptyList();
      }

      boolean atLeastOneBranchExists = false;
      for (String branchName : filterCollection.getBranchFilter().getBranchNames()) {
        if (branchName.equals(RECENT_HEAD) || branchExists(repository, branchName)) {
          filterParameters.add(prepareParameter("branch", branchName));
          atLeastOneBranchExists = true;
        }
      }
      if (!atLeastOneBranchExists) { // no such branches => filter matches nothing
        return Collections.emptyList();
      }
    }

    if (filterCollection.getUserFilter() != null) {
      for (String authorName : filterCollection.getUserFilter().getUserNames(root)) {
        filterParameters.add(prepareParameter("user", authorName));
      }
    }

    if (filterCollection.getDateFilter() != null) {
      StringBuilder args = new StringBuilder();
      final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      filterParameters.add("-r");
      VcsLogDateFilter filter = filterCollection.getDateFilter();
      if (filter.getAfter() != null) {
        args.append("date('>").append(dateFormatter.format(filter.getAfter())).append("')");
      }

      if (filter.getBefore() != null) {
        if (args.length() > 0) {
          args.append(" and ");
        }

        args.append("date('<").append(dateFormatter.format(filter.getBefore())).append("')");
      }
      filterParameters.add(args.toString());
    }

    if (filterCollection.getTextFilter() != null) {
      String textFilter = filterCollection.getTextFilter().getText();
      filterParameters.add(prepareParameter("keyword", textFilter));
    }

    if (filterCollection.getStructureFilter() != null) {
      for (VirtualFile file : filterCollection.getStructureFilter().getFiles(root)) {
        filterParameters.add(file.getPath());
      }
    }

    return HgHistoryUtil.readAllHashes(myProject, root, Consumer.EMPTY_CONSUMER, filterParameters);
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) throws VcsException {
    String userName = HgConfig.getInstance(myProject, root).getNamedConfig("ui", "username");
    //order of variables to identify hg username see at mercurial/ui.py
    if (userName == null) {
      userName = System.getenv("HGUSER");
      if (userName == null) {
        userName = System.getenv("USER");
        if (userName == null) {
          userName = System.getenv("LOGNAME");
          if (userName == null) {
            return null;
          }
        }
      }
    }
    Pair<String, String> userArgs = HgUtil.parseUserNameAndEmail(userName);
    return myVcsObjectsFactory.createUser(userArgs.getFirst(), userArgs.getSecond());
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    return HgHistoryUtil.getDescendingHeadsOfBranches(myProject, root, commitHash);
  }

  @Override
  public boolean supportsFastUnorderedCommits() {
    return false;
  }

  private static String prepareParameter(String paramName, String value) {
    return "--" + paramName + "=" + value; // no value escaping needed, because the parameter itself will be quoted by GeneralCommandLine
  }

  private static boolean branchExists(@NotNull HgRepository repository, @NotNull String branchName) {
    return repository.getBranches().keySet().contains(branchName) ||
           HgUtil.getNamesWithoutHashes(repository.getBookmarks()).contains(branchName);
  }

}
