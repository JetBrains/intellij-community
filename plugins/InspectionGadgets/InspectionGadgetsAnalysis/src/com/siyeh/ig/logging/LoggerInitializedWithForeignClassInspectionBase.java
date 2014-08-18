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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LoggerInitializedWithForeignClassInspectionBase extends BaseInspection {
  @NonNls private static final String DEFAULT_LOGGER_CLASS_NAMES =
    "org.apache.log4j.Logger,org.slf4j.LoggerFactory,org.apache.commons.logging.LogFactory,java.util.logging.Logger";
  @NonNls private static final String DEFAULT_FACTORY_METHOD_NAMES = "getLogger,getLogger,getLog,getLogger";
  protected final List<String> loggerFactoryClassNames = new ArrayList();
  protected final List<String> loggerFactoryMethodNames = new ArrayList();
  @SuppressWarnings({"PublicField"})
  public String loggerClassName = DEFAULT_LOGGER_CLASS_NAMES;
  @SuppressWarnings({"PublicField"})
  public String loggerFactoryMethodName = DEFAULT_FACTORY_METHOD_NAMES;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LoggerInitializedWithForeignClassFix((String)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoggerInitializedWithForeignClassVisitor();
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerClassName, loggerFactoryClassNames);
    parseString(loggerFactoryMethodName, loggerFactoryMethodNames);
    if (loggerFactoryClassNames.size() != loggerFactoryMethodNames.size() || loggerFactoryClassNames.isEmpty()) {
      parseString(DEFAULT_LOGGER_CLASS_NAMES, loggerFactoryClassNames);
      parseString(DEFAULT_FACTORY_METHOD_NAMES, loggerFactoryMethodNames);
    }
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerFactoryClassNames);
    loggerFactoryMethodName = formatString(loggerFactoryMethodNames);
    super.writeSettings(element);
  }

  private static class LoggerInitializedWithForeignClassFix extends InspectionGadgetsFix {

    private final String newClassName;

    private LoggerInitializedWithForeignClassFix(String newClassName) {
      this.newClassName = newClassName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.quickfix", newClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace foreign class";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)element;
      PsiReplacementUtil.replaceExpression(classObjectAccessExpression, newClassName + ".class");
    }
  }

  private class LoggerInitializedWithForeignClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
      super.visitClassObjectAccessExpression(expression);
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
        if (!expression.equals(referenceExpression.getQualifierExpression())) {
          return;
        }
        @NonNls final String name = referenceExpression.getReferenceName();
        if (!"getName".equals(name)) {
          return;
        }
        final PsiElement grandParent = referenceExpression.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
          return;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiExpressionList list = methodCallExpression.getArgumentList();
        if (list.getExpressions().length != 0) {
          return;
        }
        parent = methodCallExpression.getParent();
      }
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      while (containingClass instanceof PsiAnonymousClass) {
        containingClass = ClassUtils.getContainingClass(containingClass);
      }
      if (containingClass == null) {
        return;
      }
      final String containingClassName = containingClass.getName();
      if (containingClassName == null) {
        return;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      final int index = loggerFactoryClassNames.indexOf(className);
      if (index < 0) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      final String loggerFactoryMethodName = loggerFactoryMethodNames.get(index);
      if (!loggerFactoryMethodName.equals(referenceName)) {
        return;
      }
      final PsiTypeElement operand = expression.getOperand();
      final PsiType type = operand.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass initializerClass = classType.resolve();
      if (initializerClass == null) {
        return;
      }
      if (containingClass.equals(initializerClass)) {
        return;
      }
      registerError(expression, containingClassName);
    }
  }
}
