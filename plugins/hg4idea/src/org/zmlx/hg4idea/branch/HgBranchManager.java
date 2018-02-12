/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.log.HgRefManager;

public class HgBranchManager extends DvcsBranchManager {
  public HgBranchManager(@NotNull HgProjectSettings settings) {
    super(settings.getFavoriteBranchSettings(), HgBranchType.values());
  }

  @Nullable
  @Override
  protected String getDefaultBranchName(@NotNull BranchType type) {
    return type == HgBranchType.BRANCH ? HgRefManager.DEFAULT : null;
  }
}
