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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.ProjectScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiNewExpression newExpression = (PsiNewExpression)infos[0];
    PsiLocalVariable localVariable = tryCast(newExpression.getParent(), PsiLocalVariable.class);
    if (localVariable != null) {
      PsiType type = localVariable.getType();
      PsiClassReferenceType referenceType = tryCast(type, PsiClassReferenceType.class);
      if (referenceType == null) return null;
      PsiClass aClass = referenceType.resolve();
      if(aClass == null) return null;
      String qualifiedName = aClass.getQualifiedName();
      if(qualifiedName == null) return null;
      if (!qualifiedName.equals(CommonClassNames.JAVA_UTIL_MAP)) return null;
      // Needed to perform type migration otherwise
    }
    return new ReplaceWithEnumMapFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MapReplaceableByEnumMapVisitor();
  }

  private static class ReplaceWithEnumMapFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.fix.name");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("map.replaceable.by.enum.map.fix.family");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiJavaCodeReferenceElement codeReferenceElement = tryCast(descriptor.getStartElement(), PsiJavaCodeReferenceElement.class);
      if (codeReferenceElement == null) return;
      PsiNewExpression newExpression = tryCast(codeReferenceElement.getParent(), PsiNewExpression.class);
      if (newExpression == null) return;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return;
      PsiElement[] children = argumentList.getChildren();
      if (children.length == 0) return;
      PsiElement referenceNameElement = codeReferenceElement.getReferenceNameElement();
      if (referenceNameElement == null) return;
      PsiClassReferenceType referenceType = tryCast(newExpression.getType(), PsiClassReferenceType.class);
      if (referenceType == null) return;
      PsiType[] parameters = referenceType.getParameters();
      if (parameters.length != 2) return;
      String enumParameterText = parameters[0].getCanonicalText();
      String valueParameterText = parameters[1].getCanonicalText();
      String replacementText = "new java.util.EnumMap<" + enumParameterText + "," + valueParameterText + ">(" + enumParameterText + ".class)";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiElement result = newExpression.replace(factory.createExpressionFromText(replacementText, newExpression));
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
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
      return Arrays.asList(CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_MAP);
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
