/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.javadoc;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PackageDotHtmlMayBePackageInfoInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (((Boolean)infos[1]).booleanValue()) {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.exists.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageDotHtmlMayBePackageInfoVisitor();
  }

  private static class PackageDotHtmlMayBePackageInfoVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(PsiFile file) {
      super.visitFile(file);
      if (!(file instanceof XmlFile)) {
        return;
      }
      @NonNls final String fileName = file.getName();
      if (!"package.html".equals(fileName)) {
        return;
      }
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory == null) {
        return;
      }
      final String aPackage = getPackage(directory);
      if (aPackage == null) {
        return;
      }
      final boolean exists = directory.findFile("package-info.java") != null;
      registerError(file, aPackage, Boolean.valueOf(exists));
    }

    public static String getPackage(@NotNull PsiDirectory directory) {
      final VirtualFile virtualFile = directory.getVirtualFile();
      final Project project = directory.getProject();
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      final ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
      return fileIndex.getPackageNameByDirectory(virtualFile);
    }
  }
}
