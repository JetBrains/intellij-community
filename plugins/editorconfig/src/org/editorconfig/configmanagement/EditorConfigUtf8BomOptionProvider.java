// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class EditorConfigUtf8BomOptionProvider implements Utf8BomOptionProvider {
  @Override
  public boolean shouldAddBOMForNewUtf8File(@NotNull VirtualFile file) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    if (project != null) {
      List<EditorConfig.OutPair> optionsList = SettingsProviderComponent.getInstance().getOutPairs(project, file);
      String encoding = Utils.configValueForKey(optionsList, ConfigEncodingManager.charsetKey);
      return ConfigEncodingManager.UTF8_BOM_ENCODING.equals(encoding);
    }
    return false;
  }
}
