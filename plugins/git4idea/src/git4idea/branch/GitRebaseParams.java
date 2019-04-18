// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.rebase.GitRebaseEditorHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static java.util.Arrays.asList;

public class GitRebaseParams {

  @Nullable private final String myBranch;
  @Nullable private final String myNewBase;
  @NotNull private final String myUpstream;
  private final boolean myInteractive;
  private final boolean myPreserveMerges;
  private final Boolean myAutoSquash;
  @Nullable private final GitRebaseEditorHandler myEditorHandler;

  @NotNull
  public static GitRebaseParams editCommits(@NotNull String base, @Nullable GitRebaseEditorHandler editorHandler, boolean preserveMerges) {
    return new GitRebaseParams(null, null, base, true, preserveMerges, editorHandler);
  }

  @NotNull
  public static GitRebaseParams editCommits(@NotNull String base,
                                            @Nullable GitRebaseEditorHandler editorHandler,
                                            boolean preserveMerges,
                                            boolean autoSquash) {
    return new GitRebaseParams(null, null, base, true, preserveMerges, autoSquash, editorHandler);
  }

  public GitRebaseParams(@NotNull String upstream) {
    this(null, null, upstream, false, false);
  }

  public GitRebaseParams(@Nullable String branch,
                         @Nullable String newBase,
                         @NotNull String upstream,
                         boolean interactive,
                         boolean preserveMerges) {
    this(branch, newBase, upstream, interactive, preserveMerges, null);
  }

  private GitRebaseParams(@Nullable String branch,
                          @Nullable String newBase,
                          @NotNull String upstream,
                          boolean interactive,
                          boolean preserveMerges,
                          @Nullable GitRebaseEditorHandler editorHandler) {
    myBranch = nullize(branch, true);
    myNewBase = nullize(newBase, true);
    myUpstream = upstream;
    myInteractive = interactive;
    myPreserveMerges = preserveMerges;
    myAutoSquash = null;
    myEditorHandler = editorHandler;
  }

  private GitRebaseParams(@Nullable String branch,
                          @Nullable String newBase,
                          @NotNull String upstream,
                          boolean interactive,
                          boolean preserveMerges,
                          boolean autoSquash,
                          @Nullable GitRebaseEditorHandler editorHandler) {
    myBranch = nullize(branch, true);
    myNewBase = nullize(newBase, true);
    myUpstream = upstream;
    myInteractive = interactive;
    myPreserveMerges = preserveMerges;
    myAutoSquash = autoSquash;
    myEditorHandler = editorHandler;
  }

  @NotNull
  public List<String> asCommandLineArguments() {
    List<String> args = ContainerUtil.newArrayList();
    if (myInteractive) {
      args.add("--interactive");
    }
    if (myPreserveMerges) {
      args.add("--preserve-merges");
    }
    if (myAutoSquash != null) {
      if (myAutoSquash) {
        args.add("--autosquash");
      }
      else {
        args.add("--no-autosquash");
      }
    }
    if (myNewBase != null) {
      args.addAll(asList("--onto", myNewBase));
    }
    args.add(myUpstream);
    if (myBranch != null) {
      args.add(myBranch);
    }
    return args;
  }

  @Nullable
  public String getNewBase() {
    return myNewBase;
  }

  @NotNull
  public String getUpstream() {
    return myUpstream;
  }

  @Override
  public String toString() {
    return StringUtil.join(asCommandLineArguments(), " ");
  }

  public boolean isInteractive() {
    return myInteractive;
  }

  @Nullable
  public String getBranch() {
    return myBranch;
  }

  @Nullable
  public GitRebaseEditorHandler getEditorHandler() {
    return myEditorHandler;
  }
}
