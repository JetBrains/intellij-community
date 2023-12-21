/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.ide.IdeView;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VcsDataKeys#VIRTUAL_FILES}
 */
public class VcsVirtualFilesRule implements GetDataRule {
  private static final Logger LOG = Logger.getInstance(VcsVirtualFilesRule.class);

  @Nullable
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataProvider);
    if (files != null) {
      return JBIterable.of(files);
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataProvider);
    if (file != null) {
      return JBIterable.of(file);
    }

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataProvider);
    if (view != null) {
      JBIterable<PsiDirectory> directories = JBIterable.of(view.getDirectories());
      if (directories.isNotEmpty()) {
        if (directories.contains(null)) {
          LOG.error("Array with null provided by " + view.getClass().getName());
        }
        return directories.filterNotNull().filterMap(o -> o.getVirtualFile()).collect();
      }
    }

    return null;
  }
}