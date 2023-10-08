// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCommitTooltipLinkHandler extends TooltipLinkHandler {
  private static final Logger LOG = Logger.getInstance(GitCommitTooltipLinkHandler.class);

  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return false;

    Hash hash = tryCreateHash(refSuffix);
    if (hash == null) {
      LOG.warn("Bad revision: " + refSuffix);
      return false;
    }

    GitShowCommitInLogAction.jumpToRevision(project, hash);
    return true;
  }

  public static @Nullable String createLink(@NotNull @Nls String text, @NotNull VcsRevisionNumber revisionNumber) {
    Hash hash = tryCreateHash(revisionNumber.asString());
    if (hash == null) return null;
    return HtmlChunk.link("#git_commit/" + hash.asString(), text).toString();
  }

  private static @Nullable Hash tryCreateHash(@NotNull String revision) {
    try {
      return HashImpl.build(revision);
    }
    catch (Exception e) {
      return null;
    }
  }
}
