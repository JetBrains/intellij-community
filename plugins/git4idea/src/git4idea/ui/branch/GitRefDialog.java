// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.google.common.collect.Streams;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.impl.VcsGoToRefComparator;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.actions.VcsRefCompletionProvider;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitRemoteBranch;
import git4idea.log.GitRefManager;
import git4idea.repo.GitRepository;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.ui.UI.PanelFactory.grid;
import static com.intellij.util.ui.UI.PanelFactory.panel;

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

    TextCompletionProvider completionProvider = getCompletionProvider(project, repositories);

    myTextField = new TextFieldWithCompletion(project, completionProvider, "", true, true, false);

    myCenterPanel = grid()
      .add(panel(new JBLabel(message)))
      .add(panel(myTextField))
      .createPanel();

    init();
  }

  @NotNull
  private static TextCompletionProvider getCompletionProvider(@NotNull Project project,
                                                              @NotNull List<GitRepository> repositories) {
    VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
    if (logManager != null) {
      List<VirtualFile> roots = ContainerUtil.map(repositories, Repository::getRoot);
      DataPack dataPack = logManager.getDataManager().getDataPack();
      Map<VirtualFile, VcsLogProvider> logProviders = dataPack.getLogProviders();
      if (!logProviders.isEmpty()) {
        VcsGoToRefComparator comparator = new VcsGoToRefComparator(logProviders);
        return new MyVcsRefCompletionProvider(dataPack.getRefsModel(), roots, comparator);
      }
    }
    List<GitBranch> branches = collectCommonBranches(repositories);
    return new MyTextFieldWithAutoCompletionListProvider(branches);
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
  private static Collection<VcsRef> collectCommonVcsRefs(@NotNull Stream<VcsRef> stream) {
    MultiMap<VirtualFile, VcsRef> map = MultiMap.create();
    stream.forEach(ref -> map.putValue(ref.getRoot(), ref));

    Stream<Collection<VcsRef>> groups = map.entrySet().stream().map(Map.Entry::getValue);
    return collectCommon(groups, new NameAndTypeHashingStrategy());
  }

  @NotNull
  private static List<GitBranch> collectCommonBranches(@NotNull List<GitRepository> repositories) {
    Collection<GitLocalBranch> commonLocalBranches = collectCommon(repositories.stream().map(repository -> {
      return repository.getBranches().getLocalBranches();
    }));

    Collection<GitRemoteBranch> commonRemoteBranches = collectCommon(repositories.stream().map(repository -> {
      return repository.getBranches().getRemoteBranches();
    }));

    return Streams.concat(commonLocalBranches.stream(), commonRemoteBranches.stream())
                  .collect(Collectors.toList());
  }

  @NotNull
  private static <T> Collection<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups) {
    return collectCommon(groups, ContainerUtil.canonicalStrategy());
  }

  @NotNull
  private static <T> Collection<T> collectCommon(@NotNull Stream<? extends Collection<T>> groups,
                                                 @NotNull TObjectHashingStrategy<T> hashingStrategy) {
    List<T> common = new ArrayList<>();
    boolean[] firstGroup = {true};

    groups.forEach(values -> {
      if (firstGroup[0]) {
        firstGroup[0] = false;
        common.addAll(values);
      }
      else {
        common.retainAll(new THashSet<>(values, hashingStrategy));
      }
    });

    return common;
  }

  private static class MyVcsRefCompletionProvider extends VcsRefCompletionProvider {
    public MyVcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                                      @NotNull Collection<VirtualFile> roots,
                                      @NotNull Comparator<VcsRef> comparator) {
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

  private static class MyTextFieldWithAutoCompletionListProvider extends TextFieldWithAutoCompletionListProvider<GitBranch> {
    private final Comparator<GitBranch> myComparator = Comparator.<GitBranch, Boolean>comparing(branch -> branch.isRemote())
      .thenComparing(GitReference::getName, StringUtil::naturalCompare);

    public MyTextFieldWithAutoCompletionListProvider(@NotNull List<GitBranch> branches) {
      super(branches);
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull GitBranch branch) {
      return branch.getName();
    }

    @Override
    public int compare(@NotNull GitBranch branch1, @NotNull GitBranch branch2) {
      return myComparator.compare(branch1, branch2);
    }
  }

  private static class VcsRefDescriptor extends DefaultTextCompletionValueDescriptor<VcsRef> {
    @NotNull private final Comparator<VcsRef> myReferenceComparator;

    private VcsRefDescriptor(@NotNull Comparator<VcsRef> comparator) {
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
      return Comparing.equal(o1.getName(), o2.getName()) &&
             Comparing.equal(o1.getType(), o2.getType());
    }
  }
}
