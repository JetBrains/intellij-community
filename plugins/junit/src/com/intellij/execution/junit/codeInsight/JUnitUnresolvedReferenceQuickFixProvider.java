/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.MissingDependencyFixUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.quickFix.MissingDependencyFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.execution.junit.JUnit3Framework;
import com.intellij.execution.junit.JUnit4Framework;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class JUnitUnresolvedReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull final PsiJavaCodeReferenceElement reference, @NotNull QuickFixActionRegistrar registrar) {
    final PsiElement psiElement = reference.getElement();
    @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return;

    final VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;

    final JavaTestFramework framework;
    @NonNls final String className;
    if ("TestCase".equals(referenceName)) {
      framework = TestFramework.EXTENSION_NAME.findExtension(JUnit3Framework.class);
      className = "junit.framework.TestCase";
    }
    else if (PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class) != null && isJunitAnnotationName(referenceName, psiElement)) {
      framework = TestFramework.EXTENSION_NAME.findExtension(JUnit4Framework.class);
      className = "org.junit." + referenceName;
    }
    else {
      return;
    }

    PsiClass found = JavaPsiFacade.getInstance(project).findClass(className, currentModule.getModuleWithDependenciesAndLibrariesScope(true));
    if (found != null) return;

    final OrderEntryFix platformFix = new OrderEntryFix() {
      @Override
      @NotNull
      public String getText() {
        return QuickFixBundle.message("orderEntry.fix.add.junit.jar.to.classpath");
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return getText();
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return !project.isDisposed() && !currentModule.isDisposed();
      }

      @Override
      public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
        List<String> jarPaths = framework.getLibraryPaths();
        String libraryName = jarPaths.size() == 1 ? null : framework.getName();
        addJarsToRootsAndImportClass(jarPaths, libraryName, currentModule, editor, reference, className);
      }
    };

    final OrderEntryFix providedFix = MissingDependencyFixUtil.provideFix(new Function<MissingDependencyFixProvider, OrderEntryFix>() {
      @Override
      public OrderEntryFix fun(MissingDependencyFixProvider provider) {
        return provider.getJUnitFix(reference, platformFix, currentModule, framework, className);
      }
    });
    final OrderEntryFix fix = ObjectUtils.notNull(providedFix, platformFix);

    registrar.register(fix);
  }

  private static boolean isJunitAnnotationName(@NonNls final String referenceName, @NotNull final PsiElement psiElement) {
    if ("Test".equals(referenceName) || "Ignore".equals(referenceName) || "RunWith".equals(referenceName) ||
        "Before".equals(referenceName) || "BeforeClass".equals(referenceName) ||
        "After".equals(referenceName) || "AfterClass".equals(referenceName)) {
      return true;
    }
    final PsiElement parent = psiElement.getParent();
    if (parent != null && !(parent instanceof PsiAnnotation)) {
      final PsiReference reference = parent.getReference();
      if (reference != null) {
        final String referenceText = parent.getText();
        if (isJunitAnnotationName(reference.getRangeInElement().substring(referenceText), parent)) {
          final int lastDot = referenceText.lastIndexOf('.');
          return lastDot > -1 && referenceText.substring(0, lastDot).equals("org.junit");
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
