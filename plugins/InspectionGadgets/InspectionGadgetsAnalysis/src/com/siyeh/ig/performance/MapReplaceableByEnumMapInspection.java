/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class MapReplaceableByEnumMapInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MapReplaceableByEnumMapVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length != 1) return null;
    PsiLocalVariable localVariable = (PsiLocalVariable)infos[0];
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
    PsiNewExpression newExpression = tryCast(initializer, PsiNewExpression.class);
    if (newExpression == null) return null;
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null || !argumentList.isEmpty()) return null;
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return null;
    PsiReferenceParameterList parameterList = classReference.getParameterList();
    if (parameterList == null) return null;
    PsiClassType classType = tryCast(newExpression.getType(), PsiClassType.class);
    if (classType == null) return null;
    if (classType.getParameterCount() != 2) return null;
    PsiType[] parameters = classType.getParameters();
    PsiType enumParameter = parameters[0];
    String parameterListText = Arrays.stream(parameters).map(p -> p.getCanonicalText()).collect(Collectors.joining(",", "<", ">"));
    PsiClass probablyEnum = PsiUtil.resolveClassInClassTypeOnly(enumParameter);
    if (probablyEnum == null || !probablyEnum.isEnum()) return null;
    String text = "new java.util.EnumMap" + parameterListText + "(" + enumParameter.getCanonicalText() + ".class)";
    return new EnumMapReplacingFix(text);
  }

  private static class EnumMapReplacingFix extends InspectionGadgetsFix {
    private final String newEnumMapText;

    private EnumMapReplacingFix(String text) {newEnumMapText = text;}

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiNewExpression.class);
      if (newExpression == null) return;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(newExpression, newEnumMapText);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.fix.name");
    }
  }

  private static class MapReplaceableByEnumMapVisitor extends CollectionReplaceableByEnumCollectionVisitor {

    @Override
    @NotNull
    protected List<String> getUnreplaceableCollectionNames() {
      return Arrays.asList(CommonClassNames.JAVA_UTIL_CONCURRENT_HASH_MAP, "java.util.concurrent.ConcurrentSkipListMap",
                           "java.util.LinkedHashMap");
    }

    @NotNull
    @Override
    protected List<String> getReplaceableCollectionNames() {
      return Collections.singletonList(CommonClassNames.JAVA_UTIL_HASH_MAP);
    }

    @Override
    @NotNull
    protected String getReplacementCollectionName() {
      return "java.util.EnumMap";
    }

    @Override
    @NotNull
    protected String getBaseCollectionName() {
      return CommonClassNames.JAVA_UTIL_MAP;
    }

  }
}
