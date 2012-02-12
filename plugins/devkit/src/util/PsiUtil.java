/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.idea.devkit.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class PsiUtil {
  private PsiUtil() {
  }

  public static boolean isInstantiatable(@NotNull PsiClass cls) {
    final PsiModifierList modifiers = cls.getModifierList();

    if (modifiers == null
        || cls.isInterface()
        || modifiers.hasModifierProperty(PsiModifier.ABSTRACT)
        || !isPublicOrStaticInnerClass(cls)) {
      return false;
    }

    final PsiMethod[] constructors = cls.getConstructors();

    if (constructors.length == 0) return true;

    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParameters().length == 0
          && constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isPublicOrStaticInnerClass(@NotNull PsiClass cls) {
    final PsiModifierList modifiers = cls.getModifierList();
    if (modifiers == null) return false;

    return modifiers.hasModifierProperty(PsiModifier.PUBLIC) &&
           (cls.getParent() instanceof PsiFile || modifiers.hasModifierProperty(PsiModifier.STATIC));
  }

  public static boolean isOneStatementMethod(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    return body != null
           && body.getStatements().length == 1
           && body.getStatements()[0] instanceof PsiReturnStatement;
  }

  @Nullable
  public static String getReturnedLiteral(PsiMethod method, PsiClass cls) {
    if (isOneStatementMethod(method)) {
      final PsiExpression value = ((PsiReturnStatement)method.getBody().getStatements()[0]).getReturnValue();
      if (value instanceof PsiLiteralExpression) {
        final Object str = ((PsiLiteralExpression)value).getValue();
        return str == null ? null : str.toString();
      } else if (value instanceof PsiMethodCallExpression) {
        if (isSimpleClassNameExpression((PsiMethodCallExpression)value)) {
          return cls.getName();
        }
      }
    }
    return null;
  }

  private static boolean isSimpleClassNameExpression(PsiMethodCallExpression expr) {
    String text = expr.getText();
    if (text == null) return false;
    text = text.replaceAll(" ", "")
               .replaceAll("\n", "")
               .replaceAll("\t", "")
               .replaceAll("\r", "");
    return "getClass().getSimpleName()".equals(text) || "this.getClass().getSimpleName()".equals(text); 
  }

  @Nullable
  public static PsiExpression getReturnedExpression(PsiMethod method) {
    if (isOneStatementMethod(method)) {
      return ((PsiReturnStatement)method.getBody().getStatements()[0]).getReturnValue();
    } else {
      return null;
    }
  }
}
