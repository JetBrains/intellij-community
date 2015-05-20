/*
 * Copyright 2011-2015 Bas Leijdekkers
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
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EmptyDirectoryInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportDirectoriesUnderSourceRoots = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("empty.directory.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "empty.directories.only.under.source.roots.option"), this,
                                          "onlyReportDirectoriesUnderSourceRoots");
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager,
    @NotNull final GlobalInspectionContext context,
    @NotNull final ProblemDescriptionsProcessor processor) {
    final Project project = context.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final SearchScope searchScope = scope.toSearchScope();
    if (!(searchScope instanceof GlobalSearchScope)) {
      return;
    }
    final GlobalSearchScope globalSearchScope = (GlobalSearchScope)searchScope;
    final PsiManager psiManager = PsiManager.getInstance(project);
    index.iterateContent(new ContentIterator() {
      @Override
      public boolean processFile(final VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory()) {
          return true;
        }
        if (!globalSearchScope.contains(fileOrDir)) {
          return true;
        }
        if (onlyReportDirectoriesUnderSourceRoots && !index.isInSourceContent(fileOrDir)) {
          return true;
        }
        final VirtualFile[] children = fileOrDir.getChildren();
        if (children.length != 0) {
          return true;
        }
        final Application application = ApplicationManager.getApplication();
        final PsiDirectory directory = application.runReadAction(
          new Computable<PsiDirectory>() {
            @Override
            public PsiDirectory compute() {
              return psiManager.findDirectory(fileOrDir);
            }
          });
        final RefElement refDirectory = context.getRefManager().getReference(directory);
        if (context.shouldCheck(refDirectory, EmptyDirectoryInspection.this)) {
          return true;
        }
        final String relativePath = getPathRelativeToModule(fileOrDir, project);
        if (relativePath == null) {
          return true;
        }
        processor.addProblemElement(refDirectory, manager.createProblemDescriptor(InspectionGadgetsBundle.message(
          "empty.directories.problem.descriptor", relativePath), new EmptyPackageFix(fileOrDir.getUrl(), fileOrDir.getName())));
        return true;
      }
    });
  }

  @Nullable
  private static String getPathRelativeToModule(VirtualFile file, Project project) {
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    final Application application = ApplicationManager.getApplication();
    final VirtualFile[] contentRoots = application.runReadAction(
      new Computable<VirtualFile[]>() {
        @Override
        public VirtualFile[] compute() {
          return rootManager.getContentRootsFromAllModules();
        }
      });
    for (VirtualFile otherRoot : contentRoots) {
      if (VfsUtilCore.isAncestor(otherRoot, file, false)) {
        return VfsUtilCore.getRelativePath(file, otherRoot, '/');
      }
    }
    return null;
  }

  private static class EmptyPackageFix implements QuickFix {

    private final String url;
    private final String name;

    public EmptyPackageFix(String url, String name) {
      this.url = url;
      this.name = name;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message(
        "empty.directories.delete.quickfix", name);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) {
        return;
      }
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiDirectory directory = psiManager.findDirectory(file);
      if (directory == null) {
        return;
      }
      directory.delete();
    }
  }
}
