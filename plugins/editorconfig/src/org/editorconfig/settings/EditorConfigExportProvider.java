// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.settings;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allow custom IDEs (JetBrains Rider in particular) to provide special .editorconfig export
 */
public interface EditorConfigExportProvider {
  boolean doExport(@NotNull Project project);
  boolean shouldShowExportButton();
}
