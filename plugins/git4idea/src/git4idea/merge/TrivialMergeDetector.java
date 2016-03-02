/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitLogParser;
import git4idea.history.GitLogRecord;
import git4idea.history.browser.SHAHash;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static git4idea.history.GitLogParser.GitLogOption.BODY;
import static git4idea.history.GitLogParser.GitLogOption.HASH;
import static git4idea.history.GitLogParser.GitLogOption.SUBJECT;

/**
 * Detect a trivial merge
 * Trivial merge happens when we are merging a single commit
 * It makes no sense to avoid fast-forwarding such commits
 * */


public class TrivialMergeDetector {
  private final HashSet<String> myUnmergedPaths = new HashSet<String>();
  private final Project myProject;
  private final VirtualFile myRoot;
  private final String myStart;
  private final String myMergeFrom;

  public TrivialMergeDetector(final Project project, final VirtualFile root, final String mergeFrom) {
    myProject = project;
    myRoot = root;
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    GitRepository repository = assertNotNull(manager.getRepositoryForRoot(root));
    myStart = repository.getCurrentRevision();
    myMergeFrom = mergeFrom;
  }

  public boolean isTrivial() throws VcsException {
    String root = myRoot.getPath();
    GitLogParser parser = new GitLogParser(myProject, HASH, SUBJECT);
    GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.LOG);
    h.setSilent(true);
    // --first-parent: if a commit is a merge, we are not interested in its content
    // -n 2: get last two commits
    h.addParameters("--name-status", "-n 2", "--first-parent", myMergeFrom);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.endOptions();

    String output = h.run();

    final List<Pair<SHAHash, String>> rc = new ArrayList<Pair<SHAHash, String>>();
    for (GitLogRecord record : parser.parse(output)) {
      record.setUsedHandler(h);
      rc.add(Pair.create(new SHAHash(record.getHash()), record.getSubject()));
    }

    // TODO: parse and check
    if (rc.size()==2) {
      // the commit before is the head, the merged branch is trivial
      if (rc.get(1).first.getValue().equals(myStart)) {
        return true;
      }
    }
    return false;
  }
}
