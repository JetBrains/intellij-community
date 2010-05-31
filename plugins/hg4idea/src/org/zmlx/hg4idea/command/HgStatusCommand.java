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

import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import org.apache.commons.lang.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;

import java.io.*;
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
    return doExecute(repo, null);
  }

  public HgChange execute(VirtualFile repo, @NotNull String file) {
    Set<HgChange> changes = doExecute(repo, file);
    return changes.isEmpty() ? null : changes.iterator().next();
  }

  private Set<HgChange> doExecute(VirtualFile repo, String file) {
    if (repo == null) {
      return Collections.emptySet();
    }

    HgCommandService service = HgCommandService.getInstance(project);

    List<String> arguments = new LinkedList<String>();
    if (includeAdded) {
      arguments.add("--added");
    }
    if (includeModified) {
      arguments.add("--modified");
    }
    if (includeRemoved) {
      arguments.add("--removed");
    }
    if (includeDeleted) {
      arguments.add("--deleted");
    }
    if (includeUnknown) {
      arguments.add("--unknown");
    }
    if (includeIgnored) {
      arguments.add("--ignored");
    }
    if (includeCopySource) {
      arguments.add("--copies");
    }
    if (baseRevision != null) {
      arguments.add("--rev");
      arguments.add(baseRevision.getChangeset());
      if (targetRevision != null) {
        arguments.add("--rev");
        arguments.add(targetRevision.getChangeset());
      }
    }

    if (file != null) {
      arguments.add(file);
    }

    HgCommandResult result = service.execute(repo, "status", arguments);
    Set<HgChange> changes = new HashSet<HgChange>();
    HgChange previous = null;
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
