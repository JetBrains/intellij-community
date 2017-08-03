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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgConfig;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgChangesetUtil;
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
    List<VcsCommitMetadata> commits = HgHistoryUtil.loadMetadata(myProject, root, requirements.getCommitCount(),
                                                                 Collections.emptyList());
    return new LogDataImpl(readAllRefs(root), commits);
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    Set<VcsUser> userRegistry = ContainerUtil.newHashSet();
    List<TimedVcsCommit> commits = HgHistoryUtil.readAllHashes(myProject, root, new CollectConsumer<>(userRegistry),
                                                               Collections.emptyList());
    for (TimedVcsCommit commit : commits) {
      commitConsumer.consume(commit);
    }
    return new LogDataImpl(readAllRefs(root), userRegistry);
  }

  @Override
  public void readAllFullDetails(@NotNull VirtualFile root, @NotNull Consumer<VcsFullCommitDetails> commitConsumer) throws VcsException {
    readFullDetails(root, ContainerUtil.newArrayList(), commitConsumer);
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<VcsFullCommitDetails> commitConsumer,
                              boolean fast)
    throws VcsException {
    // parameter fast is currently not used
    // since this method is not called from index yet, fast always is false
    // but when implementing indexing mercurial commits, we'll need to avoid rename/move detection when fast = true
    // also we'll need to process commits one by one
    
    HgVcs hgvcs = HgVcs.getInstance(myProject);
    assert hgvcs != null;
    final HgVersion version = hgvcs.getVersion();
    final String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);

    HgCommandResult logResult = HgHistoryUtil.getLogResult(myProject, root, version, -1,
                                                           HgHistoryUtil.prepareHashes(hashes), HgChangesetUtil.makeTemplate(templates));
    if (logResult == null) return;
    if (!logResult.getErrorLines().isEmpty()) throw new VcsException(logResult.getRawError());
    HgHistoryUtil.createFullCommitsFromResult(myProject, root, logResult, version, false).forEach(commitConsumer::consume);
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
  private Set<VcsRef> readAllRefs(@NotNull VirtualFile root) throws VcsException {
    if (myProject.isDisposed()) {
      return Collections.emptySet();
    }
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return Collections.emptySet();
    }

    repository.update();
    Map<String, LinkedHashSet<Hash>> branches = repository.getBranches();
    Set<String> openedBranchNames = repository.getOpenedBranches();
    Collection<HgNameWithHashInfo> bookmarks = repository.getBookmarks();
    Collection<HgNameWithHashInfo> tags = repository.getTags();
    Collection<HgNameWithHashInfo> localTags = repository.getLocalTags();
    Collection<HgNameWithHashInfo> mqAppliedPatches = repository.getMQAppliedPatches();

    Set<VcsRef> refs = new HashSet<>(branches.size() + bookmarks.size());

    for (Map.Entry<String, LinkedHashSet<Hash>> entry : branches.entrySet()) {
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
      refs.add(myVcsObjectsFactory.createRef(myVcsObjectsFactory.createHash(currentRevision), HEAD_REFERENCE, HgRefManager.HEAD, root));
    }
    String tipRevision = repository.getTipRevision();
    if (tipRevision != null) { // null => fresh repository
      refs.add(myVcsObjectsFactory.createRef(myVcsObjectsFactory.createHash(tipRevision), TIP_REFERENCE, HgRefManager.TIP, root));
    }
    for (HgNameWithHashInfo tagInfo : tags) {
      refs.add(myVcsObjectsFactory.createRef(tagInfo.getHash(), tagInfo.getName(), HgRefManager.TAG, root));
    }
    for (HgNameWithHashInfo localTagInfo : localTags) {
      refs.add(myVcsObjectsFactory.createRef(localTagInfo.getHash(), localTagInfo.getName(),
                                             HgRefManager.LOCAL_TAG, root));
    }
    for (HgNameWithHashInfo mqPatchRef : mqAppliedPatches) {
      refs.add(myVcsObjectsFactory.createRef(mqPatchRef.getHash(), mqPatchRef.getName(),
                                             HgRefManager.MQ_APPLIED_TAG, root));
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

  @NotNull
  @Override
  public Disposable subscribeToRootRefreshEvents(@NotNull final Collection<VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(HgVcs.STATUS_TOPIC, new HgUpdater() {
      @Override
      public void update(Project project, @Nullable VirtualFile root) {
        if (root != null && roots.contains(root)) {
          refresher.refresh(root);
        }
      }
    });
    return connection;
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull final VirtualFile root,
                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                       int maxCount) throws VcsException {
    List<String> filterParameters = ContainerUtil.newArrayList();

    // branch filter and user filter may be used several times without delimiter
    VcsLogBranchFilter branchFilter = filterCollection.getBranchFilter();
    if (branchFilter != null) {
      HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null) {
        LOG.error("Repository not found for root " + root);
        return Collections.emptyList();
      }

      Collection<String> branchNames = repository.getBranches().keySet();
      Collection<String> bookmarkNames = HgUtil.getNamesWithoutHashes(repository.getBookmarks());
      Collection<String> predefinedNames = ContainerUtil.list(TIP_REFERENCE);

      boolean atLeastOneBranchExists = false;
      for (String branchName : ContainerUtil.concat(branchNames, bookmarkNames, predefinedNames)) {
        if (branchFilter.matches(branchName)) {
          filterParameters.add(HgHistoryUtil.prepareParameter("branch", branchName));
          atLeastOneBranchExists = true;
        }
      }

      if (branchFilter.matches(HEAD_REFERENCE)) {
        filterParameters.add(HgHistoryUtil.prepareParameter("branch", "."));
        filterParameters.add("-r");
        filterParameters.add("::."); //all ancestors for current revision;
        atLeastOneBranchExists = true;
      }

      if (!atLeastOneBranchExists) { // no such branches => filter matches nothing
        return Collections.emptyList();
      }
    }

    if (filterCollection.getUserFilter() != null) {
      filterParameters.add("-r");
      String authorFilter =
        StringUtil.join(ContainerUtil.map(ContainerUtil.map(filterCollection.getUserFilter().getUsers(root), VcsUserUtil::toExactString),
                                          UserNameRegex.EXTENDED_INSTANCE), "|");
      filterParameters.add("user('re:" + authorFilter + "')");
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
      if (filterCollection.getTextFilter().isRegex()) {
        filterParameters.add("-r");
        filterParameters.add("grep(r'" + textFilter + "')");
      }
      else if (filterCollection.getTextFilter().matchesCase()) {
        filterParameters.add("-r");
        filterParameters.add("grep(r'" + StringUtil.escapeChars(textFilter, UserNameRegex.EXTENDED_REGEX_CHARS) + "')");
      }
      else {
        filterParameters.add(HgHistoryUtil.prepareParameter("keyword", textFilter));
      }
    }

    if (filterCollection.getStructureFilter() != null) {
      for (FilePath file : filterCollection.getStructureFilter().getFiles()) {
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
  public String getCurrentBranch(@NotNull VirtualFile root) {
    HgRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) return null;
    return repository.getCurrentBranchName();
  }

  @Nullable
  @Override
  public VcsLogDiffHandler getDiffHandler() {
    return null;
  }

  @Nullable
  @Override
  public <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property) {
    if (property == VcsLogProperties.CASE_INSENSITIVE_REGEX) {
      return (T)Boolean.FALSE;
    }
    return null;
  }
}
