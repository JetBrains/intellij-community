/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @NotNull
  public List<VcsException> getExceptions() {
    return Collections.emptyList();
  }

  public void onRefreshFilesCompleted() {
  }

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
