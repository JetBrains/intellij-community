// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.*;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.log.HgFileRevisionLogParser;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HgLogCommand {

  private static final Logger LOG = Logger.getInstance(HgLogCommand.class.getName());

  @NotNull private final Project myProject;
  @NotNull private HgVersion myVersion;
  private boolean myIncludeRemoved;
  private boolean myFollowCopies;
  private boolean myLogFile = true;
  private boolean myLargeFilesWithFollowSupported = false;

  public void setIncludeRemoved(boolean includeRemoved) {
    myIncludeRemoved = includeRemoved;
  }

  public void setFollowCopies(boolean followCopies) {
    myFollowCopies = followCopies;
  }

  public void setLogFile(boolean logFile) {
    myLogFile = logFile;
  }

  public HgLogCommand(@NotNull Project project) {
    myProject = project;
    HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) {
      LOG.info("Vcs couldn't be null for project");
      return;
    }
    myVersion = vcs.getVersion();
    myLargeFilesWithFollowSupported = myVersion.isLargeFilesWithFollowSupported();
  }

  /**
   * @param limit Pass -1 to set no limits on history
   */
  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles) {
    return execute(hgFile, limit, includeFiles, null);
  }

  @NotNull
  public HgVersion getVersion() {
    return myVersion;
  }

  /**
   * @param limit Pass -1 to set no limits on history
   */
  public final List<HgFileRevision> execute(final HgFile hgFile, int limit, boolean includeFiles, @Nullable List<String> argsForCmd) {
    if ((limit <= 0 && limit != -1) || hgFile == null) {
      return Collections.emptyList();
    }

    String[] templates = HgBaseLogParser.constructFullTemplateArgument(includeFiles, myVersion);
    String template = HgChangesetUtil.makeTemplate(templates);
    FilePath originalFileName = HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(myProject));
    HgFile originalHgFile = new HgFile(hgFile.getRepo(), originalFileName);
    HgCommandResult result = execute(hgFile.getRepo(), template, limit, originalHgFile, argsForCmd);

    return HgHistoryUtil.getCommitRecords(myProject, result,
                                          new HgFileRevisionLogParser(myProject, originalHgFile, myVersion));
  }

  @NotNull
  private List<String> createArguments(@NotNull String template, int limit, @Nullable HgFile hgFile, @Nullable List<String> argsForCmd) {
    List<String> arguments = new LinkedList<>();
    if (myIncludeRemoved) {
      // There is a bug in mercurial that causes --follow --removed <file> to cause
      // an error (http://mercurial.selenic.com/bts/issue2139). Avoid this combination
      // for now, preferring to use --follow over --removed.
      if (!(myFollowCopies && myLogFile)) {
        arguments.add("--removed");
      }
    }
    if (myFollowCopies) {
      arguments.add("--follow");
      //workaround: --follow  options doesn't work with largefiles extension, so we need to switch off this extension in log command
      //see http://selenic.com/pipermail/mercurial-devel/2013-May/051209.html  fixed since 2.7
      if (!myLargeFilesWithFollowSupported) {
        arguments.add("--config");
        arguments.add("extensions.largefiles=!");
      }
    }
    arguments.add("--template");
    arguments.add(template);
    if (limit != -1) {
      arguments.add("--limit");
      arguments.add(String.valueOf(limit));
    }
    if (argsForCmd != null) {
      arguments.addAll(argsForCmd);
    }  //to do  double check the same condition should be simplified

    if (myLogFile && hgFile != null) {
      arguments.add(hgFile.getRelativePath());
    }
    return arguments;
  }

  @Nullable
  public HgCommandResult execute(@NotNull VirtualFile repo, @NotNull String template, int limit, @Nullable HgFile hgFile,
                                 @Nullable List<String> argsForCmd) {
    ShellCommand.CommandResultCollector collector = new ShellCommand.CommandResultCollector(false);
    boolean success = execute(repo, template, limit, hgFile, argsForCmd, collector);
    return success ? collector.getResult() : null;
  }

  public boolean execute(@NotNull VirtualFile repo, @NotNull String template, int limit, @Nullable HgFile hgFile,
                         @Nullable List<String> argsForCmd, @NotNull HgLineProcessListener listener) {
    List<String> arguments = createArguments(template, limit, hgFile, argsForCmd);
    HgCommandExecutor commandExecutor = new HgCommandExecutor(myProject);
    commandExecutor.setOutputAlwaysSuppressed(true);
    return commandExecutor.executeInCurrentThread(repo, "log", arguments, false, listener);
  }
}
