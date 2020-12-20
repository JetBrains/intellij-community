/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Information about one stash.
 */
public class StashInfo {
  private final @NotNull VirtualFile myRoot;
  private final @NotNull Hash myHash;
  private final @NotNull String myStash; // stash codename (stash@{1})
  private final @Nullable String myBranch;
  private final @NotNull String myMessage;
  private final @Nls String myText; // The formatted text representation

  public StashInfo(@NotNull VirtualFile root, @NotNull Hash hash, @NotNull @NlsSafe String stash, @Nullable @NlsSafe String branch, @NlsSafe @Nls String message) {
    myRoot = root;
    myHash = hash;
    myStash = stash;
    myBranch = branch;
    myMessage = message;

    HtmlBuilder sb = new HtmlBuilder();
    sb.append(HtmlChunk.text(stash).wrapWith("tt").bold()).append(": ");
    if (branch != null) {
      sb.append(HtmlChunk.text(branch).italic()).append(": ");
    }
    sb.append(message);
    myText = sb.wrapWithHtmlBody().toString();
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public Hash getHash() {
    return myHash;
  }

  @Override
  public String toString() {
    return myText;
  }

  @NlsSafe
  @NotNull
  public String getStash() {
    return myStash;
  }

  @Nullable
  public String getBranch() {
    return myBranch;
  }

  @NlsSafe
  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Nls
  public String getText() {
    return myText;
  }
}
