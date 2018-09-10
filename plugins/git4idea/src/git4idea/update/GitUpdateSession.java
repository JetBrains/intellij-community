// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.util.containers.MultiMap;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.getShortNames;
import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;

/**
 * Git update session implementation
 */
public class GitUpdateSession implements UpdateSession {
  private final boolean myResult;
  @NotNull private final Map<GitRepository, String> mySkippedRoots;

  public GitUpdateSession(boolean result, @NotNull Map<GitRepository, String> roots) {
    myResult = result;
    mySkippedRoots = roots;
  }

  @Override
  @NotNull
  public List<VcsException> getExceptions() {
    return Collections.emptyList();
  }

  @Override
  public void onRefreshFilesCompleted() {
  }

  @Override
  public boolean isCanceled() {
    return !myResult;
  }

  @Nullable
  @Override
  public String getAdditionalNotificationContent() {
    if (mySkippedRoots.isEmpty()) return null;

    if (mySkippedRoots.size() == 1) {
      GitRepository repo = mySkippedRoots.keySet().iterator().next();
      return getShortRepositoryName(repo) + " was skipped (" + mySkippedRoots.get(repo) + ")";
    }

    String prefix = "Skipped " + mySkippedRoots.size() + " repositories: <br/>";
    MultiMap<String, GitRepository> grouped = groupByReasons(mySkippedRoots);
    if (grouped.keySet().size() == 1) {
      String reason = grouped.keySet().iterator().next();
      return prefix + getShortNames(grouped.get(reason)) + " (" + reason + ")";
    }

    return prefix + StringUtil.join(grouped.keySet(), reason -> getShortNames(grouped.get(reason)) + " (" + reason + ")", "<br/>");
  }

  @NotNull
  private static MultiMap<String, GitRepository> groupByReasons(@NotNull Map<GitRepository, String> skippedRoots) {
    MultiMap<String, GitRepository> result = MultiMap.create();
    skippedRoots.forEach((file, s) -> result.putValue(s, file));
    return result;
  }
}
