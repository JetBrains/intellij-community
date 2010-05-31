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

import com.intellij.openapi.project.*;
import org.zmlx.hg4idea.*;

import java.util.*;

public class HgParentsCommand extends HgChangesetsCommand{

  private HgRevisionNumber revision;

  public HgParentsCommand(Project project) {
    super(project, "parents");
  }

  public void setRevision(HgRevisionNumber revision) {
    this.revision = revision;
  }

  @Override
  protected void addArguments(List<String> args) {
    if (revision != null) {
      args.add("--rev");
      args.add(revision.getChangeset());
    }
  }
}
