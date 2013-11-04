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
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;

/**
 * @author Nadya Zabrodina
 */
public class HgRootChecker extends VcsRootChecker {

  @Override
  public boolean isRoot(@NotNull String path) {
    return new File(path, HgUtil.DOT_HG).exists();
  }

  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @Override
  public boolean isVcsDir(String path) {
    return path != null && path.toLowerCase().endsWith(HgUtil.DOT_HG);
  }
}
