/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  @Nullable private final GitRebaseEditorHandler myEditorHandler;

  @NotNull
  public static GitRebaseParams editCommits(@NotNull String base, @Nullable GitRebaseEditorHandler editorHandler, boolean preserveMerges) {
    return new GitRebaseParams(null, null, base, true, preserveMerges, editorHandler);
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
