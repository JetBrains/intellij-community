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

import com.intellij.dvcs.test.DvcsTestPlatformFacade;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.*;

/**
 * @author Nadya Zabrodina
 */
class HgTestPlatformFacade extends DvcsTestPlatformFacade implements HgPlatformFacade {
  private HgMockVcs myVcs;
  private HgMockVcsManager myVcsManager;
  private HgTestRepositoryManager myRepositoryManager;

  HgTestPlatformFacade() {
    myRepositoryManager = new HgTestRepositoryManager();
  }

  @NotNull
  @Override
  public HgVcs getVcs(@NotNull Project project) {
    if (myVcs == null) {
      HgGlobalSettings globalSettings = new HgGlobalSettings();
      myVcs = new HgMockVcs(project, globalSettings, new HgProjectSettings(globalSettings), new HgMockVcsManager(project, this));
    }
    return myVcs;
  }

  @NotNull
  @Override
  public ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    if (myVcsManager == null) {
      myVcsManager = new HgMockVcsManager(project, this);
    }
    return myVcsManager;
  }


  @Override
  public void showDialog(@NotNull DialogWrapper dialog) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public HgRepositoryManager getRepositoryManager(@NotNull Project project) {
    return myRepositoryManager;
  }
}

