/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.dvcs.DvcsPlatformFacadeImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nadya Zabrodina
 */
public class HgPlatformFacadeImpl extends DvcsPlatformFacadeImpl implements HgPlatformFacade {

  @NotNull
  @Override
  public HgVcs getVcs(@NotNull Project project) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return (HgVcs)vcsManager.findVcsByName(HgVcs.VCS_NAME);
  }
}
