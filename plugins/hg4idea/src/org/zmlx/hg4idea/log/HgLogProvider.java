// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.EmptyConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgConfig;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.zmlx.hg4idea.log.HgHistoryUtil.getObjectsFactoryWithDisposeCheck;
import static org.zmlx.hg4idea.log.HgHistoryUtil.getOriginalHgFile;
import static org.zmlx.hg4idea.util.HgUtil.HEAD_REFERENCE;
import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public final class HgLogProvider implements VcsLogProvider {
  private static final Logger LOG = Logger.getInstance(HgLogProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public HgLogProvider(@NotNull Project project) {
    myProject = project;
    myRefSorter = new HgRefManager(project);
    myVcsObjectsFactory = project.getService(VcsLogObjectsFactory.class);
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
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<? super TimedVcsCommit> commitConsumer) throws VcsException {
    Set<VcsUser> userRegistry = new HashSet<>();
    List<TimedVcsCommit> commits = HgHistoryUtil.readAllHashes(myProject, root, new CollectConsumer<>(userRegistry),
                                                               Collections.emptyList());
    for (TimedVcsCommit commit : commits) {
      commitConsumer.consume(commit);
    }
    return new LogDataImpl(readAllRefs(root), userRegistry);
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer)
    throws VcsException {
    // parameter isForIndexing is currently not used
    // since this method is not called from index yet, fast always is false
    // but when implementing indexing mercurial commits, we'll need to avoid rename/move detection when isForIndexing = true

    HgVcs hgvcs = HgVcs.getInstance(myProject);
    assert hgvcs != null;
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, hgvcs.getVersion());
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(myProject);
    if (factory == null) {
      return;
    }

    HgFileRevisionLogParser parser = new HgFileRevisionLogParser(myProject, getOriginalHgFile(myProject, root), hgvcs.getVersion());
    HgHistoryUtil.readLog(myProject, root, hgvcs.getVersion(), -1,
                          HgHistoryUtil.prepareHashes(hashes),
                          HgChangesetUtil.makeTemplate(templates),
                          stringBuilder -> {
                            HgFileRevision revision = parser.convert(stringBuilder.toString());
                            if (revision != null) {
                              commitConsumer.consume(HgHistoryUtil.createDetails(myProject, root, factory, revision));
                            }
                          });
  }

  @Override
  public void readMetadata(@NotNull VirtualFile root, @NotNull List<String> hashes, @NotNull Consumer<? super VcsCommitMetadata> consumer)
    throws VcsException {
    HgHistoryUtil.readCommitMetadata(myProject, root, hashes, consumer);
  }

  @NotNull
  private Set<VcsRef> readAllRefs(@NotNull VirtualFile root) {
    if (myProject.isDisposed()) {
      return Collections.emptySet();
    }
    HgRepository repository = getHgRepoManager(myProject).getRepositoryForRoot(root);
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
  public Disposable subscribeToRootRefreshEvents(@NotNull final Collection<? extends VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    MessageBusConnection connection = myProject.getMessageBus().connect();
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
                                                       int maxCount) {
    List<String> filterParameters = new ArrayList<>();

    // branch filter and user filter may be used several times without delimiter
    VcsLogBranchFilter branchFilter = filterCollection.get(VcsLogFilterCollection.BRANCH_FILTER);
    if (branchFilter != null) {
      HgRepository repository = getHgRepoManager(myProject).getRepositoryForRoot(root);
      if (repository == null) {
        LOG.error("Repository not found for root " + root);
        return Collections.emptyList();
      }

      Collection<String> branchNames = repository.getBranches().keySet();
      Collection<String> bookmarkNames = HgUtil.getNamesWithoutHashes(repository.getBookmarks());
      Collection<String> predefinedNames = Collections.singletonList(TIP_REFERENCE);

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

    VcsLogUserFilter userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER);
    if (userFilter != null) {
      filterParameters.add("-r");
      String authorFilter = StringUtil.join(ContainerUtil.map(ContainerUtil.map(userFilter.getUsers(root), VcsUserUtil::toExactString),
                                                              UserNameRegex.EXTENDED_INSTANCE), "|");
      filterParameters.add("user('re:" + authorFilter + "')");
    }

    VcsLogDateFilter dateFilter = filterCollection.get(VcsLogFilterCollection.DATE_FILTER);
    if (dateFilter != null) {
      StringBuilder args = new StringBuilder();
      final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      filterParameters.add("-d");
      if (dateFilter.getAfter() != null) {
        if (dateFilter.getBefore() != null) {
          args.append(dateFormatter.format(dateFilter.getAfter())).append(" to ").append(dateFormatter.format(dateFilter.getBefore()));
        }
        else {
          args.append('>').append(dateFormatter.format(dateFilter.getAfter()));
        }
      }

      else if (dateFilter.getBefore() != null) {
        args.append('<').append(dateFormatter.format(dateFilter.getBefore()));
      }
      filterParameters.add(args.toString());
    }

    VcsLogTextFilter textFilter = filterCollection.get(VcsLogFilterCollection.TEXT_FILTER);
    if (textFilter != null) {
      String text = textFilter.getText();
      if (textFilter.isRegex()) {
        filterParameters.add("-r");
        filterParameters.add("grep(r'" + text + "')");
      }
      else if (textFilter.matchesCase()) {
        filterParameters.add("-r");
        filterParameters.add("grep(r'" + StringUtil.escapeChars(text, UserNameRegex.EXTENDED_REGEX_CHARS) + "')");
      }
      else {
        filterParameters.add(HgHistoryUtil.prepareParameter("keyword", text));
      }
    }

    VcsLogStructureFilter structureFilter = filterCollection.get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter != null) {
      for (FilePath file : structureFilter.getFiles()) {
        filterParameters.add(file.getPath());
      }
    }

    return HgHistoryUtil.readHashes(myProject, root, EmptyConsumer.getInstance(), maxCount, filterParameters);
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) {
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
  @CalledInAny
  public String getCurrentBranch(@NotNull VirtualFile root) {
    HgRepository repository = getHgRepoManager(myProject).getRepositoryForRootQuick(root);
    if (repository == null) return null;
    return repository.getCurrentBranchName();
  }

  @NotNull
  private static HgRepositoryManager getHgRepoManager(@NotNull Project project) {
    return project.getService(HgRepositoryManager.class);
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
