/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.List;

/**
 * Returns the changesets which are about to be merged.
 * Equivalent to "hg merge --preview" or "hg log -r 0:source --prune dest"
 * but the latest allows templates.
 *
 * @author Kirill Likhodedov
 */
public class HgMergePreviewCommand extends HgChangesetsCommand {

  private final HgRevisionNumber mySource;
  private final HgRevisionNumber myDest;
  private final int myLimit;

  public HgMergePreviewCommand(Project project, HgRevisionNumber source, HgRevisionNumber dest, int limit) {
    super(project, "log");
    mySource = source;
    myDest = dest;
    myLimit = limit;
  }

  @Override
  protected void addArguments(List<String> args) {
    args.add("-r");
    args.add("0:" + mySource.getChangeset());
    args.add("--prune");
    args.add(myDest.getChangeset());
    args.add("--limit");
    args.add(String.valueOf(myLimit));
  }

}
