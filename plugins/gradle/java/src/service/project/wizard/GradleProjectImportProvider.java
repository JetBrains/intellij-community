// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard;

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @deprecated Use the open and link project utility function
 *
 * @see JavaGradleProjectImportBuilder
 */
@Deprecated
@ApiStatus.Internal
public final class GradleProjectImportProvider extends AbstractExternalProjectImportProvider {
  public GradleProjectImportProvider() {
    super(GradleConstants.SYSTEM_ID);
  }

  public GradleProjectImportProvider(@NotNull GradleProjectImportBuilder builder) {
    super(builder, GradleConstants.SYSTEM_ID);
  }

  @Override
  protected ProjectImportBuilder doGetBuilder() {
    return GradleProjectImportBuilder.getInstance();
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return GradleConstants.EXTENSION.equals(file.getExtension()) ||
      file.getName().endsWith("." + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION);
  }

  @Override
  public @NotNull String getFileSample() {
    return GradleInspectionBundle.message("gradle.build.script");
  }
}
