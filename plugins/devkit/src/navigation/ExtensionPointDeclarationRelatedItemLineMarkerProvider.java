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
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ExtensionPointDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  private static final NotNullFunction<ExtensionPointCandidate, Collection<? extends PsiElement>> CONVERTER =
    new NotNullFunction<ExtensionPointCandidate, Collection<? extends PsiElement>>() {
      @NotNull
      @Override
      public Collection<? extends PsiElement> fun(ExtensionPointCandidate candidate) {
        return Collections.singleton(candidate.pointer.getElement());
      }
    };

  private static final NotNullFunction<ExtensionPointCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    new NotNullFunction<ExtensionPointCandidate, Collection<? extends GotoRelatedItem>>() {
      @NotNull
      @Override
      public Collection<? extends GotoRelatedItem> fun(ExtensionPointCandidate candidate) {
        return GotoRelatedItem.createItems(Collections.singleton(candidate.pointer.getElement()), "DevKit");
      }
    };

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (element instanceof PsiField) {
      process((PsiField)element, result);
    }
  }

  private static void process(PsiField psiField, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (!isExtensionPointNameDeclarationField(psiField)) return;

    final PsiClass epClass = resolveExtensionPointClass(psiField);
    if (epClass == null) return;

    final String epName = resolveEpName(psiField);
    if (epName == null) return;


    ExtensionPointLocator locator = new ExtensionPointLocator(epClass);
    List<ExtensionPointCandidate> targets =
      ContainerUtil.filter(locator.findDirectCandidates(), new Condition<ExtensionPointCandidate>() {
        @Override
        public boolean value(ExtensionPointCandidate candidate) {
          return epName.equals(candidate.epName);
        }
      });

    final RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(AllIcons.Nodes.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle("Choose Extension Point")
      .setTooltipText("Extension Point Declaration")
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(psiField.getNameIdentifier());
    result.add(info);
  }

  @Nullable
  private static PsiClass resolveExtensionPointClass(PsiField psiField) {
    final PsiType typeParameter = PsiUtil.substituteTypeParameter(psiField.getType(),
                                                                  ExtensionPointName.class.getName(),
                                                                  0, false);
    return PsiUtil.resolveClassInClassTypeOnly(typeParameter);
  }

  private static String resolveEpName(PsiField psiField) {
    final PsiExpression initializer = psiField.getInitializer();

    PsiExpressionList expressionList = null;
    if (initializer instanceof PsiMethodCallExpression) {
      expressionList = ((PsiMethodCallExpression)initializer).getArgumentList();
    }
    else if (initializer instanceof PsiNewExpression) {
      expressionList = ((PsiNewExpression)initializer).getArgumentList();
    }
    if (expressionList == null) return null;

    final PsiExpression[] expressions = expressionList.getExpressions();
    if (expressions.length != 1) return null;

    final PsiExpression epNameExpression = expressions[0];
    final PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(psiField.getProject()).getConstantEvaluationHelper();
    final Object o = helper.computeConstantExpression(epNameExpression);
    return o instanceof String ? (String)o : null;
  }

  private static boolean isExtensionPointNameDeclarationField(PsiField psiField) {
    // *do* allow non-public
    if (!psiField.hasModifierProperty(PsiModifier.FINAL) ||
        !psiField.hasModifierProperty(PsiModifier.STATIC) ||
        psiField.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    if (!psiField.hasInitializer()) {
      return false;
    }

    final PsiExpression initializer = psiField.getInitializer();
    if (!(initializer instanceof PsiMethodCallExpression) &&
        !(initializer instanceof PsiNewExpression)) {
      return false;
    }

    final PsiClass fieldClass = PsiTypesUtil.getPsiClass(psiField.getType());
    if (fieldClass == null) {
      return false;
    }

    return ExtensionPointName.class.getName().equals(fieldClass.getQualifiedName());
  }
}
