/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class CommonCheckinProjectAction extends AbstractCommonCheckinAction {

  @Override
  @NotNull
  protected FilePath[] getRoots(@NotNull VcsContext context) {
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(context.getProject());

    return Stream.of(manager.getAllActiveVcss())
        .filter(vcs -> vcs.getCheckinEnvironment() != null)
        .flatMap(vcs -> Stream.of(manager.getRootsUnderVcs(vcs)))
        .map(VcsUtil::getFilePath)
        .toArray(FilePath[]::new);
  }

  @Override
  protected boolean approximatelyHasRoots(@NotNull VcsContext dataContext) {
    return true;
  }

  @Override
  protected String getActionName(@NotNull VcsContext dataContext) {
    return VcsBundle.message("action.name.commit.project");
  }

  @Override
  protected String getMnemonicsFreeActionName(@NotNull VcsContext context) {
    return VcsBundle.message("vcs.command.name.checkin.no.mnemonics");
  }
}
