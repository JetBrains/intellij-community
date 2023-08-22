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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;
import org.zmlx.hg4idea.HgVcs;

public class HgMercurialMenu extends StandardVcsGroup {

  @Override
  public AbstractVcs getVcs(Project project) {
    return HgVcs.getInstance(project);
  }

  @Override
  public String getVcsName(Project project) {
    return HgVcs.VCS_NAME;
  }
}
