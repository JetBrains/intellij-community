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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JavaReferenceEditorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiUtilEx {

  private PsiUtilEx() {
  }

  public static boolean isInSourceContent(PsiElement e) {
    final VirtualFile file = e.getContainingFile().getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex index = ProjectRootManager.getInstance(e.getProject()).getFileIndex();
    return index.isInContent(file);
  }

  @Nullable
  public static PsiParameter getParameterForArgument(PsiElement element) {
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

  public static boolean isStringOrCharacterLiteral(final PsiElement place) {
    if (place instanceof PsiLiteralExpression) {
      final PsiElement child = place.getFirstChild();
      if (child instanceof PsiJavaToken) {
        final IElementType tokenType = ((PsiJavaToken)child).getTokenType();
        if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isString(@NotNull PsiType type) {
    if (type instanceof PsiClassType) {
      // optimization. doesn't require resolve
      final String shortName = ((PsiClassType)type).getClassName();
      if (!Comparing.equal(shortName, CommonClassNames.JAVA_LANG_STRING_SHORT)) return false;
    }
    return CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText(false));
  }

  public static boolean isStringOrStringArray(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isString(((PsiArrayType)type).getComponentType());
    }
    else {
      return isString(type);
    }
  }

  public static Document createDocument(final String s, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() || project.isDefault()) {
      return new DocumentImpl(s);
    }
    else {
      return JavaReferenceEditorUtil.createTypeDocument(s, project);
    }
  }

  public static boolean isLanguageAnnotationTarget(final PsiModifierListOwner owner) {
    if (owner instanceof PsiMethod) {
      final PsiType returnType = ((PsiMethod)owner).getReturnType();
      if (returnType == null || !isStringOrStringArray(returnType)) {
        return false;
      }
    }
    else if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (!isStringOrStringArray(type)) {
        return false;
      }
    }
    else {
      return false;
    }
    return true;
  }
}
