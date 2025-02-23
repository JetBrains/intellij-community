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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PsiUtilEx {

  private PsiUtilEx() {
  }

  public static @Nullable PsiParameter getParameterForArgument(PsiElement element) {
    PsiElement p = element.getParent();
    if (!(p instanceof PsiExpressionList list)) return null;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiCallExpression)) return null;
    PsiExpression[] arguments = list.getExpressions();
    int i = ArrayUtil.indexOf(arguments, element);
    if (i != -1) {
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
    }
    return null;
  }

  public static boolean isStringOrCharacterLiteral(final PsiElement place) {
    return place instanceof PsiLiteralExpression && PsiUtil.isJavaToken(place.getFirstChild(), ElementType.TEXT_LITERALS);
  }

  public static boolean isString(@NotNull PsiType type) {
    if (type instanceof PsiClassType) {
      // optimization. doesn't require resolve
      final String shortName = ((PsiClassType)type).getClassName();
      if (!Objects.equals(shortName, CommonClassNames.JAVA_LANG_STRING_SHORT)) return false;
    }
    return CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText(false));
  }
  
  public static boolean isInjectionTargetType(@NotNull PsiType type) {
    return isStringOrStringArray(type) || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR);
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
      if (returnType == null || !isInjectionTargetType(returnType)) {
        return false;
      }
    }
    else if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (!isInjectionTargetType(type)) {
        return false;
      }
    }
    else {
      return false;
    }
    return true;
  }
}
