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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * @author yole
 */
public class PlatformVcsPathPresenter extends VcsPathPresenter {
  public String getPresentableRelativePathFor(final VirtualFile file) {
    return FileUtil.toSystemDependentName(file.getPath());
  }

  public String getPresentableRelativePath(final ContentRevision fromRevision, final ContentRevision toRevision) {
    FilePath fromPath = fromRevision.getFile();
    FilePath toPath = toRevision.getFile();

    final RelativePathCalculator calculator =
      new RelativePathCalculator(toPath.getIOFile().getAbsolutePath(), fromPath.getIOFile().getAbsolutePath());
    calculator.execute();
    final String result = calculator.getResult();
    return (result == null) ? null : result.replace("/", File.separator);
  }
}
