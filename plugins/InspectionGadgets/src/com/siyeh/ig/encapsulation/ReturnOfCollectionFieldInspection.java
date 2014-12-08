/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReturnOfCollectionFieldInspection extends ReturnOfCollectionFieldInspectionBase {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[1];
    final String text = referenceExpression.getText();
    if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_MAP)) {
      if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, "java.util.SortedMap")) {
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSortedMap(" + text + ')', "java.util.SortedMap");
      }
      return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableMap(" + text + ')', CommonClassNames.JAVA_UTIL_MAP);
    }
    else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SET)) {
        if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, "java.util.SortedSet")) {
          return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSortedSet(" + text + ')', "java.util.SortedSet");
        }
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSet(" + text + ')', CommonClassNames.JAVA_UTIL_SET);
      }
      else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_LIST)) {
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableList(" + text + ')', CommonClassNames.JAVA_UTIL_LIST);
      }
      return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableCollection(" + text + ')', "java.util.Collection");
    }
    return null;
  }

  private static class ReturnOfCollectionFieldFix extends InspectionGadgetsFix {

    private final String myReplacementText;
    private final String myQualifiedClassName;

    ReturnOfCollectionFieldFix(@NonNls String replacementText, String qualifiedClassName) {
      myReplacementText = replacementText;
      myQualifiedClassName = qualifiedClassName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make return collection 'unmodifiable'";
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "return.of.collection.field.quickfix", myReplacementText);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      fixContainingMethodReturnType(referenceExpression);
      PsiReplacementUtil.replaceExpressionAndShorten(referenceExpression, myReplacementText);
    }

    private void fixContainingMethodReturnType(PsiReferenceExpression referenceExpression) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method == null) {
        return;
      }
      final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement == null) {
        return;
      }
      final PsiType type = returnTypeElement.getType();
      if (!InheritanceUtil.isInheritor(type, myQualifiedClassName)) {
        return;
      }
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final Project project = referenceExpression.getProject();
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null || myQualifiedClassName.equals(aClass.getQualifiedName())) {
        return;
      }
      final PsiType[] parameters = classType.getParameters();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final String typeText;
      if (parameters.length > 0) {
        final StringBuilder builder = new StringBuilder(myQualifiedClassName);
        builder.append('<');
        boolean comma = false;
        for (PsiType parameter : parameters) {
          if (comma) {
            builder.append(',');
          } else {
            comma = true;
          }
          builder.append(parameter.getCanonicalText());
        }
        builder.append('>');
        typeText = builder.toString();
      } else {
        typeText = myQualifiedClassName;
      }
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(typeText, referenceExpression);
      final PsiElement replacement = returnTypeElement.replace(newTypeElement);
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(replacement);
      HighlightUtils.highlightElement(replacement);
    }
  }
}