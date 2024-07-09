// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class GitPushSpecParser {
  private static final Logger LOG = Logger.getInstance(GitPushSpecParser.class);

  static @Nullable String getTargetRef(@NotNull GitRepository repository, @NotNull String sourceBranchName, @NotNull List<String> specs) {
    // pushing to several pushSpecs is not supported => looking for the first one which is valid & matches the current branch
    for (String spec : specs) {
      String target = getTarget(spec, sourceBranchName);
      if (target == null) {
        LOG.info("Push spec [" + spec + "] in " + repository.getRoot() + " is invalid or doesn't match source branch " + sourceBranchName);
      }
      else {
        return target;
      }
    }
    return null;
  }

  private static @Nullable String getTarget(@NotNull String spec, @NotNull String sourceBranch) {
    String[] parts = spec.split(":");
    if (parts.length != 2) {
      return null;
    }
    String specSource = parts[0].trim();
    String specTarget = parts[1].trim();
    specSource = StringUtil.trimStart(specSource, "+");

    if (!isStarPositionValid(specSource, specTarget)) {
      return null;
    }

    String strippedSpecSource = GitBranchUtil.stripRefsPrefix(specSource);
    String strippedSourceBranch = GitBranchUtil.stripRefsPrefix(sourceBranch);
    sourceBranch = GitBranch.REFS_HEADS_PREFIX + strippedSourceBranch;

    if (strippedSpecSource.equals(GitUtil.HEAD) ||
        specSource.equals(sourceBranch) ||
        specSource.equals(strippedSourceBranch)) {
      return specTarget;
    }

    if (specSource.endsWith("*")) {
      String sourceWoStar = specSource.substring(0, specSource.length() - 1);
      if (sourceBranch.startsWith(sourceWoStar)) {
        String starMeaning = sourceBranch.substring(sourceWoStar.length());
        return specTarget.replace("*", starMeaning);
      }
    }
    return null;
  }

  private static boolean isStarPositionValid(@NotNull String source, @NotNull String target) {
    int sourceStar = source.indexOf('*');
    int targetStar = target.indexOf('*');
    return (sourceStar < 0 && targetStar < 0) || (sourceStar == source.length() - 1 && targetStar == target.length() - 1);
  }
}
