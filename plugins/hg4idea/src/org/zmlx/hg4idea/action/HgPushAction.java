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

import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;
import java.util.List;

public class HgPushAction extends HgAbstractGlobalAction {
  public HgPushAction() {
    super(AllIcons.Actions.Commit);
  }

  @Override
  public void execute(@NotNull final Project project,
                      @NotNull Collection<HgRepository> repositories,
                      @NotNull final List<HgRepository> selectedRepositories) {
    new VcsPushDialog(project, selectedRepositories).show();
  }
}
