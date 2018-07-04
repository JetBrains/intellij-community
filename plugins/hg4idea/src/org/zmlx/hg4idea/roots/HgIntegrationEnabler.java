/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgInit;
import org.zmlx.hg4idea.util.HgUtil;

public class HgIntegrationEnabler extends VcsIntegrationEnabler {

  public HgIntegrationEnabler(@NotNull HgVcs vcs) {
    super(vcs);
  }

  @Override
  protected boolean initOrNotifyError(@NotNull final VirtualFile projectDir) {
    if (HgInit.createRepository(myProject, projectDir)) {
      refreshVcsDir(projectDir, HgUtil.DOT_HG);
      return true;
    }
    return false;
  }
}
