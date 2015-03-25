/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.LogDataImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgConfig;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.zmlx.hg4idea.util.HgUtil.HEAD_REFERENCE;
import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public class HgLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(HgLogProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final HgRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public HgLogProvider(@NotNull Project project, @NotNull HgRepositoryManager repositoryManager, @NotNull VcsLogObjectsFactory factory) {
    myProject = project;
    myRepositoryManager = repositoryManager;
    myRefSorter = new HgRefManager();
    myVcsObjectsFactory = factory;
  }

  @NotNull
  @Override
  public DetailedLogData readFirstBlock(@NotNull VirtualFile root,
                                        @NotNull Requirements requirements) throws VcsException {
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) return LogDataImpl.empty();
    return HgHistoryUtil.loadMetadata(myProject, repository, requirements.getCommitCount(),
                                      Collections.<String>emptyList());
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    if (myProject.isDisposed()) return LogDataImpl.empty();
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return LogDataImpl.empty();
    }

    HgVcs hgvcs = HgVcs.getInstance(myProject);
    assert hgvcs != null;
    final HgVersion version = hgvcs.getVersion();
    Set<VcsUser> userRegistry = ContainerUtil.newHashSet();
    Set<VcsRef> vcsRefs = ContainerUtil.newHashSet(readSeparatedRefs(repository, myVcsObjectsFactory, version));
    HgHistoryUtil.readAllHashes(myProject, repository, new CollectConsumer<VcsUser>(userRegistry),
                                commitConsumer, new CollectConsumer<VcsRef>(vcsRefs),
                                Collections.<String>emptyList());

    return new LogDataImpl(vcsRefs, userRegistry);
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
  public static Set<VcsRef> readSeparatedRefs(@NotNull HgRepository repository,
                                              VcsLogObjectsFactory vcsObjectsFactory,
                                              @NotNull HgVersion version) {
    VirtualFile root = repository.getRoot();
    repository.update();
    Set<VcsRef> refs = ContainerUtil.newHashSet();
    String currentRevision = repository.getCurrentRevision();
    if (currentRevision != null) { // null => fresh repository
      refs.add(vcsObjectsFactory.createRef(vcsObjectsFactory.createHash(currentRevision), HEAD_REFERENCE, HgRefManager.HEAD, root));
    }

    // read branches separately if inplace template pattern is not supported
    if (!version.isRevsetInTemplatesSupport()) {
      Map<String, Set<Hash>> branches = repository.getBranches();
      Set<String> openedBranchNames = repository.getOpenedBranches();

      for (Map.Entry<String, Set<Hash>> entry : branches.entrySet()) {
        String branchName = entry.getKey();
        boolean opened = openedBranchNames.contains(branchName);
        for (Hash hash : entry.getValue()) {
          refs.add(vcsObjectsFactory.createRef(hash, branchName, opened ? HgRefManager.BRANCH : HgRefManager.CLOSED_BRANCH, root));
        }
      }
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
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return Collections.emptyList();
    }
    // branch filter and user filter may be used several times without delimiter
    if (filterCollection.getBranchFilter() != null) {
      boolean atLeastOneBranchExists = false;
      for (String branchName : filterCollection.getBranchFilter().getBranchNames()) {
        if (branchName.equals(TIP_REFERENCE) || branchExists(repository, branchName)) {
          filterParameters.add(HgHistoryUtil.prepareParameter("branch", branchName));
          atLeastOneBranchExists = true;
        }
        else if (branchName.equals(HEAD_REFERENCE)) {
          filterParameters.add(HgHistoryUtil.prepareParameter("branch", "."));
          filterParameters.add("-r");
          filterParameters.add("::."); //all ancestors for current revision;
          atLeastOneBranchExists = true;
        }
      }
      if (!atLeastOneBranchExists) { // no such branches => filter matches nothing
        return Collections.emptyList();
      }
    }

    if (filterCollection.getUserFilter() != null) {
      for (String authorName : filterCollection.getUserFilter().getUserNames(root)) {
        filterParameters.add(HgHistoryUtil.prepareParameter("user", authorName));
      }
    }

    if (filterCollection.getDateFilter() != null) {
      StringBuilder args = new StringBuilder();
      final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      filterParameters.add("-d");
      VcsLogDateFilter filter = filterCollection.getDateFilter();
      if (filter.getAfter() != null) {
        if (filter.getBefore() != null) {
          args.append(dateFormatter.format(filter.getAfter())).append(" to ").append(dateFormatter.format(filter.getBefore()));
        }
        else {
          args.append('>').append(dateFormatter.format(filter.getAfter()));
        }
      }

      else if (filter.getBefore() != null) {
        args.append('<').append(dateFormatter.format(filter.getBefore()));
      }
      filterParameters.add(args.toString());
    }

    if (filterCollection.getTextFilter() != null) {
      String textFilter = filterCollection.getTextFilter().getText();
      filterParameters.add(HgHistoryUtil.prepareParameter("keyword", textFilter));
    }

    if (filterCollection.getStructureFilter() != null) {
      for (VirtualFile file : filterCollection.getStructureFilter().getFiles()) {
        filterParameters.add(file.getPath());
      }
    }
    List<TimedVcsCommit> commits = ContainerUtil.newArrayList();
    HgHistoryUtil.readAllHashes(myProject, repository, Consumer.EMPTY_CONSUMER,
                                new CollectConsumer<TimedVcsCommit>(commits), Consumer.EMPTY_CONSUMER,
                                Collections.<String>emptyList());

    return commits;
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
    Couple<String> userArgs = HgUtil.parseUserNameAndEmail(userName);
    return myVcsObjectsFactory.createUser(userArgs.getFirst(), userArgs.getSecond());
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    return HgHistoryUtil.getDescendingHeadsOfBranches(myProject, root, commitHash);
  }

  @Nullable
  @Override
  public <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property) {
    return null;
  }

  private static boolean branchExists(@NotNull HgRepository repository, @NotNull String branchName) {
    return repository.getBranches().keySet().contains(branchName) ||
           HgUtil.getNamesWithoutHashes(repository.getBookmarks()).contains(branchName);
  }

}
