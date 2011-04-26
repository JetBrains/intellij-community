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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgChange;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileStatusEnum;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.io.File;
import java.util.*;

public class HgStatusCommand {

  private static final Logger LOG = Logger.getInstance(HgStatusCommand.class.getName());

  private static final int ITEM_COUNT = 3;
  private static final int STATUS_INDEX = 0;

  private final Project project;

  private boolean includeAdded = true;
  private boolean includeModified = true;
  private boolean includeRemoved = true;
  private boolean includeDeleted = true;
  private boolean includeUnknown = true;
  private boolean includeIgnored = true;
  private boolean includeCopySource = true;
  private HgRevisionNumber baseRevision;
  private HgRevisionNumber targetRevision;

  public HgStatusCommand(Project project) {
    this.project = project;
  }

  public void setIncludeAdded(boolean includeAdded) {
    this.includeAdded = includeAdded;
  }

  public void setIncludeModified(boolean includeModified) {
    this.includeModified = includeModified;
  }

  public void setIncludeRemoved(boolean includeRemoved) {
    this.includeRemoved = includeRemoved;
  }

  public void setIncludeDeleted(boolean includeDeleted) {
    this.includeDeleted = includeDeleted;
  }

  public void setIncludeUnknown(boolean includeUnknown) {
    this.includeUnknown = includeUnknown;
  }

  public void setIncludeIgnored(boolean includeIgnored) {
    this.includeIgnored = includeIgnored;
  }

  public void setIncludeCopySource(boolean includeCopySource) {
    this.includeCopySource = includeCopySource;
  }

  public void setBaseRevision(HgRevisionNumber base) {
    baseRevision = base;
  }

  public void setTargetRevision(HgRevisionNumber target) {
    targetRevision = target;
  }

  public Set<HgChange> execute(VirtualFile repo) {
    return execute(repo, null);
  }

  public Set<HgChange> execute(VirtualFile repo, @Nullable Collection<FilePath> paths) {
    if (repo == null) {
      return Collections.emptySet();
    }

    HgCommandExecutor executor = new HgCommandExecutor(project);
    executor.setSilent(true);

    List<String> options = new LinkedList<String>();
    if (includeAdded) {
      options.add("--added");
    }
    if (includeModified) {
      options.add("--modified");
    }
    if (includeRemoved) {
      options.add("--removed");
    }
    if (includeDeleted) {
      options.add("--deleted");
    }
    if (includeUnknown) {
      options.add("--unknown");
    }
    if (includeIgnored) {
      options.add("--ignored");
    }
    if (includeCopySource) {
      options.add("--copies");
    }
    if (baseRevision != null) {
      options.add("--rev");
      options.add(baseRevision.getChangeset());
      if (targetRevision != null) {
        options.add("--rev");
        options.add(targetRevision.getChangeset());
      }
    }

    final Set<HgChange> changes = new HashSet<HgChange>();

    if (paths != null) {
      final List<List<String>> chunked = VcsFileUtil.chunkPaths(repo, paths);
      for (List<String> chunk : chunked) {
        List<String> args = new ArrayList<String>();
        args.addAll(options);
        args.addAll(chunk);
        HgCommandResult result = executor.executeInCurrentThread(repo, "status", args);
        changes.addAll(parseChangesFromResult(repo, result));
      }
    } else {
      HgCommandResult result = executor.executeInCurrentThread(repo, "status", options);
      changes.addAll(parseChangesFromResult(repo, result));
    }
    return changes;
  }

  private static Collection<HgChange> parseChangesFromResult(VirtualFile repo, HgCommandResult result) {
    final Set<HgChange> changes = new HashSet<HgChange>();
    HgChange previous = null;
    if (result == null) {
      return changes;
    }
    for (String line : result.getOutputLines()) {
      if (StringUtils.isBlank(line) || line.length() < ITEM_COUNT) {
        LOG.warn("Unexpected line in status '" + line + '\'');
        continue;
      }
      HgFileStatusEnum status = HgFileStatusEnum.valueOf(line.charAt(STATUS_INDEX));
      File ioFile = new File(repo.getPath(), line.substring(2));
      if (HgFileStatusEnum.COPY == status && previous != null
        && previous.getStatus() == HgFileStatusEnum.ADDED) {
        previous.setStatus(HgFileStatusEnum.COPY);
        previous.setBeforeFile(new HgFile(repo, ioFile));
        previous = null;
      } else {
        previous = new HgChange(new HgFile(repo, ioFile), status);
        changes.add(previous);
      }
    }
    return changes;
  }
}
