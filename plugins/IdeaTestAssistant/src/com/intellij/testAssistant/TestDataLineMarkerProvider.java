package com.intellij.testAssistant;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class TestDataLineMarkerProvider implements LineMarkerProvider {
  public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return null;
    }
    final PsiMethod method = (PsiMethod)element;
    String name = method.getName();
    if (!name.startsWith("test")) {
      return null;
    }
    String testDataPath = getTestDataBasePath(method.getContainingClass());
    if (testDataPath != null) {
      List<String> fileNames = new TestDataReferenceCollector(testDataPath, name.substring(4)).collectTestDataReferences(method);
      if (fileNames.size() > 0) {
        return new LineMarkerInfo<PsiMethod>(method, method.getTextOffset(), Icons.TEST_SOURCE_FOLDER, Pass.UPDATE_ALL, null,
                                             new TestDataNavigationHandler());
      }
    }
    return null;
  }

  public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
  }

  @Nullable
  public static String getTestDataBasePath(PsiClass psiClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton("com.intellij.testFramework.TestDataPath"));
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiExpression) {
        final Project project = value.getProject();
        final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        final Object constantValue = evaluationHelper.computeConstantExpression(value, false);
        if (constantValue instanceof String) {
          String path = (String) constantValue;
          if (path.indexOf("$CONTENT_ROOT") >= 0) {
            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            final VirtualFile contentRoot = fileIndex.getContentRootForFile(psiClass.getContainingFile().getVirtualFile());
            if (contentRoot == null) return null;
            path = path.replace("$CONTENT_ROOT", contentRoot.getPath());
          }
          if (path.indexOf("$PROJECT_ROOT") >= 0) {
            final VirtualFile baseDir = project.getBaseDir();
            if (baseDir == null) {
              return null;
            }
            path = path.replace("$PROJECT_ROOT", baseDir.getPath());
          }
          return path;
        }
      }
    }
    return null;
  }

}
