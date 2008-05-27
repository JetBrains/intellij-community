/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiUtilEx {
  @NonNls
  private static final String JAVA_LANG_STRING = "java.lang.String";

  private PsiUtilEx() {
  }

  public static boolean isInSourceContent(PsiElement e) {
    final VirtualFile file = e.getContainingFile().getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex index = ProjectRootManager.getInstance(e.getProject()).getFileIndex();
    return index.isInContent(file);
  }

  @Nullable
  public static PsiParameter getParameterForArgument(PsiExpression element) {
    PsiElement p = element.getParent();
    if (!(p instanceof PsiExpressionList)) return null;
    PsiExpressionList list = (PsiExpressionList)p;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiCallExpression)) return null;
    PsiExpression[] arguments = list.getExpressions();
    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      if (argument == element) {
        final PsiCallExpression call = (PsiCallExpression)parent;
        final PsiMethod method = call.resolveMethod();
        if (method != null) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length > i) {
            return parameters[i];
          }
          else if (parameters.length > 0) {
            final PsiParameter lastParam = parameters[parameters.length - 1];
            if (lastParam.getType() instanceof PsiEllipsisType) {
              return lastParam;
            }
          }
        }
        break;
      }
    }
    return null;
  }

  public static boolean isStringLiteral(PsiElement value) {
    if (value instanceof PsiLiteralExpression) {
      final PsiLiteralExpression expression = (PsiLiteralExpression)value;
      final PsiType type = expression.getType();
      if (type != null && isString(type)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isString(@NotNull PsiType type) {
    // PsiType.equalsToText() seems to be kinda expensive (says the profiler)
    return JAVA_LANG_STRING.equals(type.getCanonicalText());
  }

  public static boolean isStringOrStringArray(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isString(((PsiArrayType)type).getComponentType());
    }
    else {
      return isString(type);
    }
  }
}
