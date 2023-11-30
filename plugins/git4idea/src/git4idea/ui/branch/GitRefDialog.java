// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.impl.VcsGoToRefComparator;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.actions.TwoStepCompletionProvider;
import com.intellij.vcs.log.ui.actions.VcsRefCompletionProvider;
import git4idea.GitBranch;
import git4idea.GitReference;
import git4idea.GitTag;
import git4idea.branch.GitBranchUtil;
import git4idea.log.GitRefManager;
import git4idea.repo.GitRepository;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.intellij.util.ui.UI.PanelFactory.grid;
import static com.intellij.util.ui.UI.PanelFactory.panel;

public final class GitRefDialog extends DialogWrapper {
  private final TextFieldWithCompletion myTextField;
  private final JComponent myCenterPanel;

  public GitRefDialog(@NotNull Project project,
                      @NotNull List<GitRepository> repositories,
                      @NotNull @NlsContexts.DialogTitle String title,
                      @NotNull @NlsContexts.Label String message) {
    super(project);
    setTitle(title);

    TextCompletionProvider completionProvider = getCompletionProvider(project, repositories, getDisposable());
    myTextField = new TextFieldWithCompletion(project, completionProvider, "", true, true, false);

    myCenterPanel = grid()
      .add(panel(new JBLabel(message)))
      .add(panel(myTextField))
      .createPanel();

    init();
  }

  private static @NotNull TextCompletionProvider getCompletionProvider(@NotNull Project project,
                                                                       @NotNull List<GitRepository> repositories,
                                                                       @NotNull Disposable disposable) {
    VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
    if (logManager != null) {
      List<VirtualFile> roots = ContainerUtil.map(repositories, Repository::getRoot);
      DataPack dataPack = logManager.getDataManager().getDataPack();
      if (dataPack != DataPack.EMPTY) {
        VcsGoToRefComparator comparator = new VcsGoToRefComparator(dataPack.getLogProviders());
        return new MyVcsRefCompletionProvider(dataPack.getRefsModel(), roots, comparator);
      }
    }
    List<GitBranch> branches = ContainerUtil.concat(GitBranchUtil.getCommonLocalBranches(repositories),
                                                    GitBranchUtil.getCommonRemoteBranches(repositories));
    Future<Collection<GitTag>> tagsFuture = scheduleCollectCommonTags(repositories, disposable);
    return new MySimpleCompletionListProvider(branches, tagsFuture);
  }

  private static Future<Collection<GitTag>> scheduleCollectCommonTags(@NotNull List<GitRepository> repositories,
                                                                            @NotNull Disposable disposable) {
    return
    ApplicationManager.getApplication().executeOnPooledThread(() ->
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, () ->
        GitBranchUtil.collectCommon(repositories.stream().map(repository -> {
          try {
            List<String> tags = GitBranchUtil.getAllTags(repository.getProject(), repository.getRoot());
            return ContainerUtil.map(tags, GitTag::new);
          }
          catch (VcsException e) {
            return Collections.emptyList();
          }
        })))
    );
  }


  public @NotNull String getReference() {
    return StringUtil.trim(myTextField.getText(), CharFilter.NOT_WHITESPACE_FILTER);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  private static @NotNull Collection<VcsRef> collectCommonVcsRefs(@NotNull Stream<? extends VcsRef> stream) {
    MultiMap<VirtualFile, VcsRef> map = MultiMap.create();
    stream.forEach(ref -> map.putValue(ref.getRoot(), ref));

    Stream<Collection<VcsRef>> groups = map.entrySet().stream().map(Map.Entry::getValue);
    return GitBranchUtil.collectCommon(groups, new NameAndTypeHashingStrategy());
  }

  private static final class MyVcsRefCompletionProvider extends VcsRefCompletionProvider {
    MyVcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                               @NotNull Collection<? extends VirtualFile> roots,
                               @NotNull Comparator<? super VcsRef> comparator) {
      super(refs, roots, new VcsRefDescriptor(comparator));
    }

    @Override
    protected @NotNull Stream<VcsRef> filterRefs(@NotNull Stream<VcsRef> vcsRefs) {
      Stream<VcsRef> branches = vcsRefs.filter(ref -> {
        VcsRefType type = ref.getType();
        return type == GitRefManager.LOCAL_BRANCH ||
               type == GitRefManager.REMOTE_BRANCH ||
               type == GitRefManager.TAG;
      });
      return collectCommonVcsRefs(branches).stream();
    }
  }

  private static final class MySimpleCompletionListProvider extends TwoStepCompletionProvider<GitReference> {
    private final @NotNull List<? extends GitBranch> myBranches;
    private final @NotNull Future<? extends Collection<GitTag>> myTagsFuture;

    MySimpleCompletionListProvider(@NotNull List<? extends GitBranch> branches,
                                   @NotNull Future<? extends Collection<GitTag>> tagsFuture) {
      super(new GitReferenceDescriptor());
      myBranches = branches;
      myTagsFuture = tagsFuture;
    }

    @Override
    protected @NotNull Stream<? extends GitReference> collectSync(@NotNull CompletionResultSet result) {
      return myBranches.stream()
        .filter(branch -> result.getPrefixMatcher().prefixMatches(branch.getName()));
    }

    @Override
    protected @NotNull Stream<? extends GitReference> collectAsync(@NotNull CompletionResultSet result) {
      try {
        return myTagsFuture.get().stream()
          .filter(tag -> result.getPrefixMatcher().prefixMatches(tag.getName()));
      }
      catch (ExecutionException | InterruptedException e) {
        return Stream.empty();
      }
    }
  }

  private static final class VcsRefDescriptor extends DefaultTextCompletionValueDescriptor<VcsRef> {
    private final @NotNull Comparator<? super VcsRef> myReferenceComparator;

    private VcsRefDescriptor(@NotNull Comparator<? super VcsRef> comparator) {
      myReferenceComparator = comparator;
    }

    @Override
    public @NotNull String getLookupString(@NotNull VcsRef item) {
      return item.getName();
    }

    @Override
    public int compare(VcsRef item1, VcsRef item2) {
      return myReferenceComparator.compare(item1, item2);
    }
  }

  private static final class NameAndTypeHashingStrategy implements Hash.Strategy<VcsRef> {
    @Override
    public int hashCode(VcsRef object) {
      return object == null ? 0 : Comparing.hashcode(object.getName(), object.getType());
    }

    @Override
    public boolean equals(VcsRef o1, VcsRef o2) {
      return o1 == o2 || (o1 != null && o2 != null && Objects.equals(o1.getName(), o2.getName()) &&
                          Objects.equals(o1.getType(), o2.getType()));
    }
  }

  private static final class GitReferenceDescriptor extends DefaultTextCompletionValueDescriptor<GitReference> {
    @Override
    public @NotNull String getLookupString(@NotNull GitReference item) {
      return item.getName();
    }

    @Override
    public int compare(GitReference item1, GitReference item2) {
      return item1.compareTo(item2);
    }
  }
}
