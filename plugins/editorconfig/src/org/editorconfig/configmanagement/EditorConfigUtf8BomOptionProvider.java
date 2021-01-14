// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

final class EditorConfigUtf8BomOptionProvider implements Utf8BomOptionProvider {
  @Override
  public boolean shouldAddBOMForNewUtf8File(@NotNull VirtualFile file) {
    if (!Utils.isApplicableTo(file)) return false;
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    return EditorConfigEncodingCache.getInstance().getUseUtf8Bom(project, file);
  }
}
