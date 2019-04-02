// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.util.List;

public final class EclipseProjectOpenProcessor extends ProjectOpenProcessorBase<EclipseImportBuilder> {
  @NotNull
  @Override
  protected EclipseImportBuilder doGetBuilder() {
    return ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(EclipseImportBuilder.class);
  }

  @Override
  @NotNull
  public String[] getSupportedExtensions() {
    return new String[] {EclipseXml.CLASSPATH_FILE, EclipseXml.PROJECT_FILE};
  }

  @Override
  public boolean doQuickImport(@NotNull VirtualFile file, @NotNull final WizardContext wizardContext) {
    getBuilder().setRootDirectory(file.getParent().getPath());

    final List<String> projects = getBuilder().getList();
    if (projects == null || projects.size() != 1) {
      return false;
    }
    getBuilder().setList(projects);
    wizardContext.setProjectName(EclipseProjectFinder.findProjectName(projects.get(0)));
    return true;
  }
}