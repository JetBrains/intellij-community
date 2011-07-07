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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.DiffRequestFromChange;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.util.BeforeAfter;

import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 5:48 PM
 */
public class BinaryDiffRequestFromChange implements DiffRequestFromChange<DiffContent> {
  private final Project myProject;

  public BinaryDiffRequestFromChange(Project project) {
    myProject = project;
  }

  @Override
  public boolean canCreateRequest(Change change) {
    return ShowDiffAction.isBinaryChangeAndCanShow(myProject, change);
  }

  @Override
  public List<BeforeAfter<DiffContent>> createRequestForChange(Change change, int extraLines) throws VcsException {
    return Collections.singletonList(ShowDiffAction.createBinaryDiffContents(myProject, change));
  }
}
