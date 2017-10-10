// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.util.PathUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.testAssistant.TestDataNavigationHandler;

import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

public class TestDataFilesReferencesContributor extends PsiReferenceContributor {
  private static final String TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME = TestDataFile.class.getCanonicalName();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    PsiJavaElementPattern.Capture<PsiLiteralExpression> capture = literalExpression().methodCallParameter(psiMethod());
    registrar.registerReferenceProvider(capture, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiLiteralExpression expression = (PsiLiteralExpression)element;
        PsiElement expressionParent = expression.getParent();
        if (!(expressionParent instanceof PsiExpressionList)) {
          return PsiReference.EMPTY_ARRAY;
        }
        PsiExpressionList expressionList = (PsiExpressionList)expressionParent;
        PsiExpression[] expressions = expressionList.getExpressions();

        int index = getExpressionIndex(expression, expressions);
        if (index < 0) {
          // shouldn't happen
          return PsiReference.EMPTY_ARRAY;
        }

        PsiMethodCallExpression methodCallExpression = getMethodCallExpression(expression);
        PsiParameter targetParameter = getTargetParameter(index, methodCallExpression);
        if (!checkTestDataFileAnnotationPresent(targetParameter)) {
          return PsiReference.EMPTY_ARRAY;
        }

        String testDataFilePath = getTestDataFilePath(expression, methodCallExpression);
        if (testDataFilePath == null) {
          return PsiReference.EMPTY_ARRAY;
        }

        String directory = PathUtil.getParentPath(testDataFilePath);
        FileReferenceSet fileReferenceSet = new FileReferenceSet(element);
        fileReferenceSet.addCustomization(
          FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION,
          ignore -> {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(directory);
            return file == null ? null : Collections.singleton(element.getManager().findDirectory(file));
          });

        return fileReferenceSet.getAllReferences();
      }
    });
  }

  private static int getExpressionIndex(@NotNull PsiLiteralExpression expression, @NotNull PsiExpression[] expressions) {
    int index = -1;
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression e = expressions[i];
      if (expression.equals(e)) {
        index = i;
        break;
      }
    }
    return index;
  }

  @Nullable
  private static PsiMethodCallExpression getMethodCallExpression(@NotNull PsiLiteralExpression expression) {
    PsiElement expressionContext = expression.getContext();
    if (expressionContext == null) {
      return null;
    }
    PsiElement methodCallExpression = expressionContext.getContext();
    if (!(methodCallExpression instanceof PsiMethodCallExpression)) {
      return null;
    }
    return (PsiMethodCallExpression)methodCallExpression;
  }

  @Nullable
  private static PsiParameter getTargetParameter(int index, @Nullable PsiMethodCallExpression methodCallExpression) {
    if (methodCallExpression == null) {
      return null;
    }
    PsiMethod callMethod = methodCallExpression.resolveMethod();
    if (callMethod == null) {
      return null;
    }

    PsiParameter[] parameters = callMethod.getParameterList().getParameters();
    if (parameters.length == 0) {
      return null;
    }
    // index may be greater than parameters length in case of varargs
    return index >= parameters.length ? parameters[parameters.length - 1] : parameters[index];
  }

  private static boolean checkTestDataFileAnnotationPresent(@Nullable PsiParameter targetParameter) {
    if (targetParameter == null) {
      return false;
    }
    PsiAnnotation[] annotations = targetParameter.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME.equals(annotation.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String getTestDataFilePath(@NotNull PsiLiteralExpression expression,
                                            @NotNull PsiMethodCallExpression methodCallExpression) {
    Object value = expression.getValue();
    if (!(value instanceof String)) {
      return null;
    }

    String relativePath = (String)value;
    PsiMethod testMethod = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethod.class);
    if (testMethod == null) {
      return null;
    }

    List<String> filePaths = TestDataNavigationHandler.fastGetTestDataPathsByRelativePath(relativePath, testMethod);
    if (filePaths.size() != 1) {
      return null;
    }
    return filePaths.get(0);
  }
}
