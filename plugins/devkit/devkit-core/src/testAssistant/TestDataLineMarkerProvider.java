// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
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
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;

import java.util.Collections;

/**
 * @author yole
 */
public class TestDataLineMarkerProvider extends RunLineMarkerContributor {
  public static final String TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME = TestDataPath.class.getCanonicalName();
  public static final String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  public static final String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";

  @Override
  public Info getInfo(@NotNull PsiElement e) {
    UElement uElement = UastUtils.getUParentForIdentifier(e);
    if (!(uElement instanceof UMethod) &&
        !(uElement instanceof UClass)) {
      return null;
    }

    final Project project = e.getProject();
    if (DumbService.isDumb(project) || !PsiUtil.isPluginProject(project)) {
      return null;
    }

    final VirtualFile file = PsiUtilCore.getVirtualFile(e);
    if (file == null || !ProjectFileIndex.SERVICE.getInstance(project).isInTestSourceContent(file)) {
      return null;
    }

    if (uElement instanceof UMethod) {
      return new Info(ActionManager.getInstance().getAction("TestData.Navigate"));
    }
    
    final PsiClass psiClass = ((UClass)uElement).getJavaPsi();
    final String testDataBasePath = getTestDataBasePath(psiClass);
    if (testDataBasePath == null) {
      return null;
    }
    return new Info(new GotoTestDataAction(testDataBasePath, psiClass.getProject(), AllIcons.Nodes.Folder));
  }

  @Nullable
  public static String getTestDataBasePath(@Nullable PsiClass psiClass) {
    if (psiClass == null) return null;

    final UAnnotation annotation =
      UastContextKt.toUElement(AnnotationUtil.findAnnotationInHierarchy(psiClass,
                                                                        Collections.singleton(TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME)),
                               UAnnotation.class);
    if (annotation != null) {
      UExpression value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null) {
        final Project project = psiClass.getProject();
        final Object constantValue = value.evaluate();
        if (constantValue instanceof String) {
          String path = (String)constantValue;
          if (path.contains(CONTENT_ROOT_VARIABLE)) {
            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
            if (file == null) {
              return null;
            }
            final VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
            if (contentRoot == null) return null;
            path = path.replace(CONTENT_ROOT_VARIABLE, contentRoot.getPath());
          }
          if (path.contains(PROJECT_ROOT_VARIABLE)) {
            String baseDir = project.getBasePath();
            if (baseDir == null) {
              return null;
            }
            path = path.replace(PROJECT_ROOT_VARIABLE, baseDir);
          }
          return path;
        }
      }
    }
    return null;
  }
}
