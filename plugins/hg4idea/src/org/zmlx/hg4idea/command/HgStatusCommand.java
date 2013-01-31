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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
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
  @Nullable private HgRevisionNumber baseRevision;
  @Nullable private HgRevisionNumber targetRevision;
  private HgPlatformFacade myPlatformFacade;

  public HgStatusCommand(Project project) {
    this(project, ServiceManager.getService(project, HgPlatformFacade.class));
  }

  public HgStatusCommand(Project project, HgPlatformFacade facade) {
    this.project = project;
    myPlatformFacade = facade;
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

  public void setBaseRevision(@Nullable HgRevisionNumber base) {
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

    HgCommandExecutor executor = new HgCommandExecutor(project, null, myPlatformFacade);
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
    if (baseRevision != null && !baseRevision.getRevision().isEmpty()) {
      options.add("--rev");
      options.add(baseRevision.getChangeset().isEmpty() ? baseRevision.getRevision() : baseRevision.getChangeset());
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
        changes.addAll(parseChangesFromResult(repo, result, args));
      }
    } else {
      HgCommandResult result = executor.executeInCurrentThread(repo, "status", options);
      changes.addAll(parseChangesFromResult(repo, result, options));
    }
    return changes;
  }

  private static Collection<HgChange> parseChangesFromResult(VirtualFile repo, HgCommandResult result, List<String> args) {
    final Set<HgChange> changes = new HashSet<HgChange>();
    HgChange previous = null;
    if (result == null) {
      return changes;
    }
    for (String line : result.getOutputLines()) {
      if (StringUtil.isEmptyOrSpaces(line) || line.length() < ITEM_COUNT) {
        LOG.warn("Unexpected line in status '" + line + '\'');
        continue;
      }
      char statusChar = line.charAt(STATUS_INDEX);
      HgFileStatusEnum status = HgFileStatusEnum.parse(statusChar);
      if (status == null) {
        LOG.error("Unknown status [" + statusChar + "] in line [" + line + "]" + "\n with arguments " + args);
        continue;
      }
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

  private void setOnlyUntrackedTrue() {
    includeAdded = false;
    includeModified = false;
    includeRemoved = false;
    includeDeleted = false;
    includeCopySource = false;
    includeIgnored = false;
    includeUnknown = true;
  }

  @NotNull
  public Collection<VirtualFile> getHgUntrackedFiles(@NotNull VirtualFile repo, @NotNull List<VirtualFile> files) throws VcsException {
    Collection<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();
    setOnlyUntrackedTrue();
    List<FilePath> filePaths = ObjectsConvertor.vf2fp(files);
    Set<HgChange> change = execute(repo, filePaths);
    for (HgChange hgChange : change) {
      untrackedFiles.add(hgChange.afterFile().toFilePath().getVirtualFile());
    }
    return untrackedFiles;
  }
}
