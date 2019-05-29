// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.export;

import com.intellij.openapi.options.SchemeExporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

public class EditorConfigExporter extends SchemeExporter<CodeStyleScheme> {
  @Override
  public void exportScheme(@Nullable Project project,
                           @NotNull CodeStyleScheme scheme,
                           @NotNull OutputStream outputStream) throws Exception {
    final CodeStyleSettings settings = scheme.getCodeStyleSettings();
    try (EditorConfigSettingsWriter writer = new EditorConfigSettingsWriter(project, outputStream, settings, false, false)) {
      writer.writeSettings();
    }
  }

  @Override
  public String getExtension() {
    return "editorconfig";
  }

  @Override
  public String getDefaultFileName(@NotNull String schemeName) {
    return "";
  }

  @NotNull
  @Override
  public VirtualFile getDefaultDir(@Nullable Project project) {
    if (project != null) {
      @SuppressWarnings("deprecation")
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir;
      }
    }
    return super.getDefaultDir(project);
  }
}
