/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GitBekParentFixer {
  @NotNull private static final String MAGIC_TEXT_FOR_IDEA_PULLS = "Merge remote";
  @NotNull private static final String MAGIC_REGEXP_FOR_COMMAND_LINE_PULLS = "^Merge branch .* of [^ ]*$";
  @NotNull private static final VcsLogFilterCollection MAGIC_IDEA_PULLS_FILTER = createIdeaPullFilterCollection();
  @NotNull private static final VcsLogFilterCollection MAGIC_COMMAND_LINE_PULLS_FILTER = createCommandLinePullFilterCollection();
  @NotNull private static final Pattern NAME_WITH_DOT = Pattern.compile("(\\w*)\\.(\\w*)");
  @NotNull private static final Pattern NAME_WITH_SPACE = Pattern.compile("(\\w*) (\\w*)");

  @NotNull private final Set<Hash> myWrongCommits;

  private GitBekParentFixer(@NotNull Set<Hash> wrongCommits) {
    myWrongCommits = wrongCommits;
  }

  @NotNull
  static GitBekParentFixer prepare(@NotNull VirtualFile root, @NotNull GitLogProvider provider) throws VcsException {
    if (!BekSorter.isBekEnabled()) {
      return new GitBekParentFixer(Collections.<Hash>emptySet());
    }
    return new GitBekParentFixer(getWrongCommits(provider, root));
  }

  @NotNull
  TimedVcsCommit fixCommit(@NotNull TimedVcsCommit commit) {
    if (!myWrongCommits.contains(commit.getId())) {
      return commit;
    }
    return reverseParents(commit);
  }

  @NotNull
  private static Set<Hash> getWrongCommits(@NotNull GitLogProvider provider, @NotNull VirtualFile root) throws VcsException {
    List<TimedVcsCommit> ideaPulls = provider.getCommitsMatchingFilter(root, MAGIC_IDEA_PULLS_FILTER, -1);
    List<TimedVcsCommit> commandLinePullCandidates = provider.getCommitsMatchingFilter(root, MAGIC_COMMAND_LINE_PULLS_FILTER, -1);

    Set<Hash> result = new HashSet<Hash>();
    Function<TimedVcsCommit, Hash> function = new Function<TimedVcsCommit, Hash>() {
      @Override
      public Hash fun(TimedVcsCommit timedVcsCommit) {
        return timedVcsCommit.getId();
      }
    };
    result.addAll(ContainerUtil.map2Set(ideaPulls, function));

    Set<Hash> commandLinePulls = new HashSet<Hash>();
    for (TimedVcsCommit candidate : commandLinePullCandidates) {
      List<Hash> parents = candidate.getParents();
      if (parents.size() < 2) continue;

      ArrayList<String> hashes = new ArrayList<String>(3);
      hashes.add(candidate.getId().asString());
      for (Hash parent : parents) {
        hashes.add(parent.asString());
      }
      List<? extends VcsShortCommitDetails> shortDetails = provider.readShortDetails(root, hashes);
      String mergeCommitter = null;
      String firstParentCommitter = null;
      for (VcsShortCommitDetails detail : shortDetails) {
        if (detail.getId().equals(candidate.getId())) {
          mergeCommitter = detail.getCommitter().getName();
        }
        else if (detail.getId().equals(parents.get(0))) {
          firstParentCommitter = detail.getCommitter().getName();
        }
      }

      if (mergeCommitter != null &&
          firstParentCommitter != null &&
          getNameInStandardForm(mergeCommitter).equals(getNameInStandardForm(firstParentCommitter))) {
        // actually, even that does not work all the time
        // a person can make a commit and then merge someones pull-request
        commandLinePulls.add(candidate.getId());
      }
    }

    result.addAll(commandLinePulls);
    return result;
  }

  @NotNull
  private static String getNameInStandardForm(@NotNull String name) {
    Matcher nameWithDotMatcher = NAME_WITH_DOT.matcher(name);
    if (nameWithDotMatcher.matches()) {
      return nameWithDotMatcher.group(1).toLowerCase() + " " + nameWithDotMatcher.group(2).toLowerCase();
    }
    Matcher nameWithSpaceMatcher = NAME_WITH_SPACE.matcher(name);
    if (nameWithSpaceMatcher.matches()) {
      return nameWithSpaceMatcher.group(1).toLowerCase() + " " + nameWithSpaceMatcher.group(2).toLowerCase();
    }
    return name.toLowerCase();
  }

  @NotNull
  private static TimedVcsCommit reverseParents(@NotNull final TimedVcsCommit commit) {
    return new TimedVcsCommit() {
      @Override
      public long getTimestamp() {
        return commit.getTimestamp();
      }

      @NotNull
      @Override
      public Hash getId() {
        return commit.getId();
      }

      @NotNull
      @Override
      public List<Hash> getParents() {
        return ContainerUtil.reverse(commit.getParents());
      }
    };
  }

  private static VcsLogFilterCollection createIdeaPullFilterCollection() {
    final VcsLogTextFilter textFilter = new VcsLogTextFilter() {
      @NotNull
      @Override
      public String getText() {
        return MAGIC_TEXT_FOR_IDEA_PULLS;
      }

      @Override
      public boolean matches(@NotNull VcsCommitMetadata details) {
        return details.getFullMessage().contains(MAGIC_TEXT_FOR_IDEA_PULLS);
      }
    };

    return new FilterCollectionWithTextFilter(textFilter);
  }

  private static VcsLogFilterCollection createCommandLinePullFilterCollection() {
    final VcsLogTextFilter textFilter = new VcsLogTextFilter() {
      private final Pattern myPattern = Pattern.compile(MAGIC_REGEXP_FOR_COMMAND_LINE_PULLS, Pattern.MULTILINE);

      @NotNull
      @Override
      public String getText() {
        return MAGIC_REGEXP_FOR_COMMAND_LINE_PULLS;
      }

      @Override
      public boolean matches(@NotNull VcsCommitMetadata details) {
        return myPattern.matcher(details.getFullMessage()).matches();
      }
    };

    return new FilterCollectionWithTextFilter(textFilter);
  }

  private static class FilterCollectionWithTextFilter implements VcsLogFilterCollection {
    private final VcsLogTextFilter myTextFilter;

    public FilterCollectionWithTextFilter(VcsLogTextFilter textFilter) {
      myTextFilter = textFilter;
    }

    @Nullable
    @Override
    public VcsLogBranchFilter getBranchFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogUserFilter getUserFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogDateFilter getDateFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogTextFilter getTextFilter() {
      return myTextFilter;
    }

    @Nullable
    @Override
    public VcsLogHashFilter getHashFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogStructureFilter getStructureFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogRootFilter getRootFilter() {
      return null;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @NotNull
    @Override
    public List<VcsLogDetailsFilter> getDetailsFilters() {
      return Collections.<VcsLogDetailsFilter>singletonList(myTextFilter);
    }
  }

  private static class EmptyFilterCollection implements VcsLogFilterCollection {
    @Nullable
    @Override
    public VcsLogBranchFilter getBranchFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogUserFilter getUserFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogDateFilter getDateFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogTextFilter getTextFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogHashFilter getHashFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogStructureFilter getStructureFilter() {
      return null;
    }

    @Nullable
    @Override
    public VcsLogRootFilter getRootFilter() {
      return null;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @NotNull
    @Override
    public List<VcsLogDetailsFilter> getDetailsFilters() {
      return null;
    }
  }
}
