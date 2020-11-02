// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class GitPushSpecParser {
  private static final Logger LOG = Logger.getInstance(GitPushSpecParser.class);

  @Nullable
  static String getTargetRef(@NotNull GitRepository repository, @NotNull String sourceBranchName, @NotNull List<String> specs) {
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

  @Nullable
  private static String getTarget(@NotNull String spec, @NotNull String sourceBranch) {
    String[] parts = spec.split(":");
    if (parts.length != 2) {
      return null;
    }
    String source = parts[0].trim();
    String target = parts[1].trim();
    source = StringUtil.trimStart(source, "+");

    if (!isStarPositionValid(source, target)) {
      return null;
    }

    source = GitBranchUtil.stripRefsPrefix(source);
    sourceBranch = GitBranchUtil.stripRefsPrefix(sourceBranch);
    if (source.equals(GitUtil.HEAD) || source.equals(sourceBranch)) return target;

    if (source.endsWith("*")) {
      String sourceWoStar = source.substring(0, source.length() - 1);
      if (sourceBranch.startsWith(sourceWoStar)) {
        String starMeaning = sourceBranch.substring(sourceWoStar.length());
        return target.replace("*", starMeaning);
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
