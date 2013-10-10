/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.UninitializedReadCollector;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InstanceVariableUninitializedUseInspectionBase extends BaseInspection {
  protected final List<String> annotationNames = new ArrayList();
  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String annotationNamesString = "";

  public InstanceVariableUninitializedUseInspectionBase() {
    parseString(annotationNamesString, annotationNames);
  }

  @Override
  @NotNull
  public String getID() {
    return "InstanceVariableUsedBeforeInitialized";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instance.variable.used.before.initialized.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("instance.variable.used.before.initialized.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(annotationNamesString, annotationNames);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    annotationNamesString = formatString(annotationNames);
    super.writeSettings(element);
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return AddToIgnoreIfAnnotatedByListQuickFix.build(field, annotationNames);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceVariableInitializationVisitor();
  }

  private class InstanceVariableInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(field, annotationNames);
      if (annotation != null) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType fieldType = field.getType();
        if (ClassUtils.isPrimitive(fieldType)) {
          return;
        }
      }
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      for (ImplicitUsageProvider provider :
        Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)) {
        if (provider.isImplicitWrite(field)) {
          return;
        }
      }
      final UninitializedReadCollector uninitializedReadsCollector = new UninitializedReadCollector();
      if (!isInitializedInInitializer(field, uninitializedReadsCollector)) {
        final PsiMethod[] constructors = aClass.getConstructors();
        for (final PsiMethod constructor : constructors) {
          final PsiCodeBlock body = constructor.getBody();
          uninitializedReadsCollector.blockAssignsVariable(body, field);
        }
      }
      final PsiExpression[] badReads = uninitializedReadsCollector.getUninitializedReads();
      for (PsiExpression expression : badReads) {
        registerError(expression, field);
      }
    }

    private boolean isInitializedInInitializer(@NotNull PsiField field, UninitializedReadCollector uninitializedReadsCollector) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiCodeBlock body = initializer.getBody();
          if (uninitializedReadsCollector.blockAssignsVariable(body, field)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
