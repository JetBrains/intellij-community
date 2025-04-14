// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.editorconfig.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

final class EditorConfigExportProviderEP {
  private static final ExtensionPointName<EditorConfigExportProvider> EP_NAME = ExtensionPointName.create("editorconfig.exportProvider");

  static boolean tryExportViaProviders(Project project) {
    for (EditorConfigExportProvider provider : EP_NAME.getExtensions()) {
      if (provider.doExport(project)) {
        return true;
      }
    }
    return false;
  }

  static boolean shouldShowExportButton() {
    for (EditorConfigExportProvider provider : EP_NAME.getExtensions()) {
      if (provider.shouldShowExportButton()) {
        return true;
      }
    }
    return false;
  }
}
