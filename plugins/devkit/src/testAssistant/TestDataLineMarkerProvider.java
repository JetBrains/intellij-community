/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class TestDataLineMarkerProvider implements LineMarkerProvider {
  public static final String TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataPath";
  public static final String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  public static final String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";

  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file == null || !ProjectFileIndex.SERVICE.getInstance(element.getProject()).isInTestSourceContent(file)) {
      return null;
    }
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (isTestMethod(method)) {
        return new LineMarkerInfo<PsiMethod>(
          method, method.getModifierList().getTextRange(), PlatformIcons.TEST_SOURCE_FOLDER, Pass.UPDATE_ALL, null, new TestDataNavigationHandler(),
          GutterIconRenderer.Alignment.LEFT);
      }                                                                                         
    } else if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      final String basePath = getTestDataBasePath(psiClass);
      if (basePath != null) {
        PsiModifierList modifierList = psiClass.getModifierList();
        assert modifierList != null;
        return new LineMarkerInfo<PsiClass>(
          psiClass, modifierList.getTextRange(), PlatformIcons.TEST_SOURCE_FOLDER, Pass.UPDATE_ALL,
          new TooltipProvider(basePath), new GutterIconNavigationHandler<PsiClass>() {
          @Override
          public void navigate(MouseEvent e, PsiClass elt) {
            final VirtualFile baseDir = VfsUtil.findFileByIoFile(new File(basePath), true);
            if (baseDir != null) {
              new OpenFileDescriptor(psiClass.getProject(), baseDir).navigate(true);
            }
          }
        },
          GutterIconRenderer.Alignment.LEFT);
      }
    }
    return null;
  }

  private static boolean isTestMethod(@NotNull PsiMethod method) {
    if (isTestMethodWithAnnotation(method)) {
      return true;
    }

    final List<String> files = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method);
    return files != null && !files.isEmpty();
  }

  private static boolean isTestMethodWithAnnotation(@NotNull PsiMethod method) {
    String name = method.getName();
    if (!name.startsWith("test")) {
      return false;
    }
    String testDataPath = getTestDataBasePath(method.getContainingClass());
    if (testDataPath == null) {
      return false;
    }
    List<String> fileNames = new TestDataReferenceCollector(testDataPath, TestDataGuessByExistingFilesUtil.getTestName(name)).collectTestDataReferences(method);
    return fileNames != null && !fileNames.isEmpty();
  }

  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }

  @Nullable
  public static String getTestDataBasePath(PsiClass psiClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(psiClass,
                                                                              Collections.singleton(TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME));
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiExpression) {
        final Project project = value.getProject();
        final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        final Object constantValue = evaluationHelper.computeConstantExpression(value, false);
        if (constantValue instanceof String) {
          String path = (String) constantValue;
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
            final VirtualFile baseDir = project.getBaseDir();
            if (baseDir == null) {
              return null;
            }
            path = path.replace(PROJECT_ROOT_VARIABLE, baseDir.getPath());
          }
          return path;
        }
      }
    }
    return null;
  }

  private static class TooltipProvider implements Function<PsiClass, String> {
    @NotNull private final String myBasePath;

    public TooltipProvider(@NotNull String basePath) {
      myBasePath = basePath;
    }

    @Override
    public String fun(PsiClass aClass) {
      return myBasePath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TooltipProvider)) return false;

      TooltipProvider provider = (TooltipProvider)o;

      if (!myBasePath.equals(provider.myBasePath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myBasePath.hashCode();
    }
  }
}
