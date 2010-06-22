/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.commands.GitFileUtils;
import git4idea.history.GitHistoryUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LowLevelAccessImpl implements LowLevelAccess {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.browser.LowLevelAccessImpl");
  private final Project myProject;
  private final VirtualFile myRoot;

  public LowLevelAccessImpl(final Project project, final VirtualFile root) {
    myProject = project;
    myRoot = root;
  }

  public GitCommit getCommitByHash(SHAHash hash) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  // todo command filters
  public List<Pair<SHAHash,Date>> loadCommitHashes(final @NotNull Collection<String> startingPoints,
                                                   @NotNull final Collection<String> endPoints,
                                                   @NotNull final Collection<ChangesFilter.Filter> filters,
                                                   int useMaxCnt) throws VcsException {
    final List<String> parameters = new LinkedList<String>();
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }

    for (ChangesFilter.Filter filter : filters) {
      filter.getCommandParametersFilter().applyToCommandLine(parameters);
    }

    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }

    for (String endPoint : endPoints) {
      parameters.add("^" + endPoint);
    }

    return GitHistoryUtils.onlyHashesHistory(myProject, new FilePathImpl(myRoot), parameters.toArray(new String[parameters.size()]));
  }

  public void loadCommits(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, final Consumer<GitCommit> consumer,
                             int maxCnt, List<String> branches) throws VcsException {
    final Collection<ChangesFilter.Filter> filters = new LinkedList<ChangesFilter.Filter>(filtersIn);
    if (beforePoint != null) {
      filters.add(new ChangesFilter.BeforeDate(new Date(beforePoint.getTime() - 1)));
    }
    if (afterPoint != null) {
      filters.add(new ChangesFilter.AfterDate(afterPoint));
    }

    loadCommits(startingPoints, Collections.<String>emptyList(), filters, consumer, branches, maxCnt);
  }

  public void loadCommits(final @NotNull Collection<String> startingPoints, @NotNull final Collection<String> endPoints,
                          @NotNull final Collection<ChangesFilter.Filter> filters,
                          @NotNull final Consumer<GitCommit> consumer, final Collection<String> branches, int useMaxCnt)
    throws VcsException {

    final List<String> parameters = new LinkedList<String>();
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }

    for (ChangesFilter.Filter filter : filters) {
      filter.getCommandParametersFilter().applyToCommandLine(parameters);
    }
    
    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }

    for (String endPoint : endPoints) {
      parameters.add("^" + endPoint);
    }

    // todo can easily be done step-by-step
    final List<GitCommit> commits =
      GitHistoryUtils.historyWithLinks(myProject, new FilePathImpl(myRoot), new HashSet<String>(branches), parameters.toArray(new String[parameters.size()]));
    for (GitCommit commit : commits) {
      consumer.consume(commit);
    }
  }

  public Collection<String> getBranchesWithCommit(final SHAHash hash) throws VcsException {
    final List<String> result = new LinkedList<String>();
    GitBranch.listAsStrings(myProject, myRoot, false, true, result, hash.getValue());
    GitBranch.listAsStrings(myProject, myRoot, true, false, result, hash.getValue());
    return result;
  }

  public Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException {
    final List<String> result = new LinkedList<String>();
    GitTag.listAsStrings(myProject, myRoot, result, hash.getValue());
    return result;
  }

  public void loadAllBranches(List<String> sink) throws VcsException {
    GitBranch.listAsStrings(myProject, myRoot, true, false, sink, null);
    GitBranch.listAsStrings(myProject, myRoot, false, true, sink, null);
  }

  public void loadAllTags(List<String> sink) throws VcsException {
    GitTag.listAsStrings(myProject, myRoot, sink, null);
  }

  public void cherryPick(SHAHash hash) throws VcsException {
    GitFileUtils.cherryPick(myProject, myRoot, hash.getValue());
  }
}
