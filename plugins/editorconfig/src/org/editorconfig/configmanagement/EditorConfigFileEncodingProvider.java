// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.FileEncodingProvider;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorConfigFileEncodingProvider implements FileEncodingProvider {
  @Override
  public boolean shouldAddBOMForNewUtf8File(@NotNull VirtualFile file) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    if (project != null) {
      List<EditorConfig.OutPair> optionsList = SettingsProviderComponent.getInstance().getOutPairs(project, file);
      String encoding = Utils.configValueForKey(optionsList, EncodingManager.charsetKey);
      return EncodingManager.UTF8_BOM_ENCODING.equals(encoding);
    }
    return false;
  }
}
