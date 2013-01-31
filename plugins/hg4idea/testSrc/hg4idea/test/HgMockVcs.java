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
package hg4idea.test;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;

/**
 * @author Nadya Zabrodina
 */
class HgMockVcs extends HgVcs {

  public HgMockVcs(Project project, HgGlobalSettings globalSettings,
                   HgProjectSettings projectSettings,
                   ProjectLevelVcsManager vcsManager) {
    super(project, globalSettings, projectSettings, vcsManager, new HgTestPlatformFacade());
  }

  @Override
  public String getDisplayName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Configurable getConfigurable() {
    throw new UnsupportedOperationException();
  }
}
