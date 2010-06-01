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
import org.zmlx.hg4idea.*;

import java.util.*;

/**
 * Abstract base class for all commands that need to parse changeset information.
 */
public abstract class HgChangesetsCommand {

  private static final Logger LOG = Logger.getInstance(HgChangesetsCommand.class.getName());

  private static final String SEPARATOR_STRING = "\u0017"; //ascii: end of transmission block
  
  private final Project project;
  private final String command;

  public HgChangesetsCommand(Project project, String command) {
    this.project = project;
    this.command = command;
  }

  public List<HgRevisionNumber> execute(VirtualFile repo) {
    return getRevisions(repo);
  }

  protected List<HgRevisionNumber> getRevisions(VirtualFile repo) {
    HgCommandService commandService = HgCommandService.getInstance(project);


    List<String> args = new ArrayList<String>(Arrays.asList(
      "--template",
      "{rev}|{node|short}|{author}|{desc|firstline}" + SEPARATOR_STRING
    ));

    addArguments(args);

    HgCommandResult result = commandService.execute(
      repo, Arrays.asList("--quiet"), command, args
    );
    
    String output = result.getRawOutput();
    if (StringUtils.isEmpty(output)) {
      return Collections.emptyList();
    }
    
    String[] changesets = output.split(SEPARATOR_STRING);
    List<HgRevisionNumber> revisions = new ArrayList<HgRevisionNumber>(changesets.length);
    
    for(String changeset: changesets) {
      String[] parts = StringUtils.split(changeset, "|", 4);
      if (parts.length == 4) {
        revisions.add(HgRevisionNumber.getInstance(parts[0], parts[1], parts[2], parts[3]));
      } else {
        LOG.warn("Could not parse changeset [" + changeset + "]");
      }
    }
    
    return revisions;
  }

  protected abstract void addArguments(List<String> args);
}
