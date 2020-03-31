// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import static com.intellij.util.ui.UI.PanelFactory.grid;
import static com.intellij.util.ui.UI.PanelFactory.panel;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.FutureResult;
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
import gnu.trove.TObjectHashingStrategy;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitRefDialog extends DialogWrapper {
  private final TextFieldWithCompletion myTextField;
  private final JComponent myCenterPanel;

  public GitRefDialog(@NotNull Project project,
                      @NotNull List<GitRepository> repositories,
                      @NotNull String title,
                      @NotNull String message) {
    super(project);

    setTitle(title);
    setButtonsAlignment(SwingConstants.CENTER);

    TextCompletionProvider completionProvider = getCompletionProvider(project, repositories, getDisposable());

    myTextField = new TextFieldWithCompletion(project, completionProvider, "", true, true, false);

    myCenterPanel = grid()
      .add(panel(new JBLabel(message)))
      .add(panel(myTextField))
      .createPanel();

    init();
  }

  @NotNull
  private static TextCompletionProvider getCompletionProvider(@NotNull Project project,
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
    FutureResult<Collection<GitTag>> tagsFuture = scheduleCollectCommonTags(repositories, disposable);
    return new MySimpleCompletionListProvider(branches, tagsFuture);
  }

  private static FutureResult<Collection<GitTag>> scheduleCollectCommonTags(@NotNull List<GitRepository> repositories,
                                                                            @NotNull Disposable disposable) {
    FutureResult<Collection<GitTag>> futureResult = new FutureResult<>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> futureResult.set(BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, () -> GitBranchUtil.collectCommon(repositories.stream().map(repository -> {
        try {
          List<String> tags = GitBranchUtil.getAllTags(repository.getProject(), repository.getRoot());
          return ContainerUtil.map(tags, GitTag::new);
        }
        catch (VcsException e) {
          return Collections.emptyList();
        }
      })))));
    return futureResult;
  }


  @NotNull
  public String getReference() {
    return myTextField.getText();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }


  @NotNull
  private static Collection<VcsRef> collectCommonVcsRefs(@NotNull Stream<? extends VcsRef> stream) {
    MultiMap<VirtualFile, VcsRef> map = MultiMap.create();
    stream.forEach(ref -> map.putValue(ref.getRoot(), ref));

    Stream<Collection<VcsRef>> groups = map.entrySet().stream().map(Map.Entry::getValue);
    return GitBranchUtil.collectCommon(groups, new NameAndTypeHashingStrategy());
  }

  private static class MyVcsRefCompletionProvider extends VcsRefCompletionProvider {
    MyVcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                                      @NotNull Collection<? extends VirtualFile> roots,
                                      @NotNull Comparator<? super VcsRef> comparator) {
      super(refs, roots, new VcsRefDescriptor(comparator));
    }

    @NotNull
    @Override
    protected Stream<VcsRef> filterRefs(@NotNull Stream<VcsRef> vcsRefs) {
      Stream<VcsRef> branches = vcsRefs.filter(ref -> {
        VcsRefType type = ref.getType();
        return type == GitRefManager.LOCAL_BRANCH ||
               type == GitRefManager.REMOTE_BRANCH ||
               type == GitRefManager.TAG;
      });
      return collectCommonVcsRefs(branches).stream();
    }
  }

  private static class MySimpleCompletionListProvider extends TwoStepCompletionProvider<GitReference> {
    @NotNull private final List<? extends GitBranch> myBranches;
    @NotNull private final FutureResult<? extends Collection<GitTag>> myTagsFuture;

    MySimpleCompletionListProvider(@NotNull List<? extends GitBranch> branches,
                                          @NotNull FutureResult<? extends Collection<GitTag>> tagsFuture) {
      super(new GitReferenceDescriptor());
      myBranches = branches;
      myTagsFuture = tagsFuture;
    }

    @NotNull
    @Override
    protected Stream<? extends GitReference> collectSync(@NotNull CompletionResultSet result) {
      return myBranches.stream()
                       .filter(branch -> result.getPrefixMatcher().prefixMatches(branch.getName()));
    }

    @NotNull
    @Override
    protected Stream<? extends GitReference> collectAsync(@NotNull CompletionResultSet result) {
      try {
        return myTagsFuture.get().stream()
                           .filter(tag -> result.getPrefixMatcher().prefixMatches(tag.getName()));
      }
      catch (ExecutionException | InterruptedException e) {
        return Stream.empty();
      }
    }
  }

  private static class VcsRefDescriptor extends DefaultTextCompletionValueDescriptor<VcsRef> {
    @NotNull private final Comparator<? super VcsRef> myReferenceComparator;

    private VcsRefDescriptor(@NotNull Comparator<? super VcsRef> comparator) {
      myReferenceComparator = comparator;
    }

    @NotNull
    @Override
    public String getLookupString(@NotNull VcsRef item) {
      return item.getName();
    }

    @Override
    public int compare(VcsRef item1, VcsRef item2) {
      return myReferenceComparator.compare(item1, item2);
    }
  }

  private static class NameAndTypeHashingStrategy implements TObjectHashingStrategy<VcsRef> {
    @Override
    public int computeHashCode(VcsRef object) {
      return Comparing.hashcode(object.getName(), object.getType());
    }

    @Override
    public boolean equals(VcsRef o1, VcsRef o2) {
      return Objects.equals(o1.getName(), o2.getName()) &&
             Comparing.equal(o1.getType(), o2.getType());
    }
  }

  private static class GitReferenceDescriptor extends DefaultTextCompletionValueDescriptor<GitReference> {
    @NotNull
    @Override
    public String getLookupString(@NotNull GitReference item) {
      return item.getName();
    }

    @Override
    public int compare(GitReference item1, GitReference item2) {
      return item1.compareTo(item2);
    }
  }
}
