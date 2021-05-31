// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public final class TestDataLineMarkerProvider extends LineMarkerProviderDescriptor {

  @Override
  public String getName() {
    return DevKitBundle.message("gutter.name.test.data.line.marker");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Folder;
  }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                     @NotNull Collection<? super LineMarkerInfo<?>> result) {
    for (PsiElement element : elements) {
      RunLineMarkerContributor.Info info = getSlowInfo(element);
      if (info != null) {
        result.add(RunLineMarkerProvider.createLineMarker(element, AllIcons.Nodes.Folder, Collections.singletonList(info)));
      }
    }
  }

  private static RunLineMarkerContributor.Info getSlowInfo(@NotNull PsiElement e) {
    final Project project = e.getProject();
    if (DumbService.isDumb(project) || !PsiUtil.isPluginProject(project)) {
      return null;
    }

    final VirtualFile file = PsiUtilCore.getVirtualFile(e);
    if (file == null || !ProjectFileIndex.SERVICE.getInstance(project).isInTestSourceContent(file)) {
      return null;
    }

    UElement uElement = UastUtils.getUParentForIdentifier(e);
    if (!(uElement instanceof UMethod) &&
        !(uElement instanceof UClass)) {
      return null;
    }

    if (uElement instanceof UMethod) {
      UElement uParent = uElement.getUastParent();
      if (uParent != null) {
        PsiElement psiClass = uParent.getJavaPsi();
        TestFramework testFramework = psiClass instanceof PsiClass && getTestDataBasePath((PsiClass)psiClass) != null ? TestFrameworks.detectFramework((PsiClass)psiClass) : null;
        if (testFramework != null && testFramework.isTestMethod(uElement.getJavaPsi())) {
          return new RunLineMarkerContributor.Info(ActionManager.getInstance().getAction("TestData.Navigate"));
        }
      }
      return null;
    }

    final PsiClass psiClass = ((UClass)uElement).getJavaPsi();
    final String testDataBasePath = getTestDataBasePath(psiClass);
    if (testDataBasePath == null) {
      return null;
    }
    return new RunLineMarkerContributor.Info(new GotoTestDataAction(testDataBasePath, psiClass.getProject(), AllIcons.Nodes.Folder));
  }

  @Nullable
  public static String getTestDataBasePath(@Nullable PsiClass psiClass) {
    if (psiClass == null) return null;

    final UAnnotation annotation =
      UastContextKt.toUElement(AnnotationUtil.findAnnotationInHierarchy(psiClass,
                                                                        Collections.singleton(TestFrameworkConstants.TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME)),
                               UAnnotation.class);
    if (annotation != null) {
      UExpression value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null) {
        final Project project = psiClass.getProject();
        final Object constantValue = value.evaluate();
        if (constantValue instanceof String) {
          String path = (String)constantValue;
          if (path.contains(TestFrameworkConstants.CONTENT_ROOT_VARIABLE)) {
            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
            if (file == null) {
              return null;
            }
            final VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
            if (contentRoot == null) return null;
            path = path.replace(TestFrameworkConstants.CONTENT_ROOT_VARIABLE, contentRoot.getPath());
          }
          if (path.contains(TestFrameworkConstants.PROJECT_ROOT_VARIABLE)) {
            String baseDir = project.getBasePath();
            if (baseDir == null) {
              return null;
            }
            path = path.replace(TestFrameworkConstants.PROJECT_ROOT_VARIABLE, baseDir);
          }
          return path;
        }
      }
    }
    return null;
  }
}
