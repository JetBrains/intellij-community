// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInsight.options.JavaIdentifierValidator;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilterBase;
import com.intellij.util.xmlb.XmlSerializer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;


public class LoggerInitializedWithForeignClassInspection extends BaseInspection {

  @NonNls private static final String DEFAULT_FACTORY_CLASS_NAMES =
    // Log4J 1
    "org.apache.log4j.Logger," +
    // SLF4J
    "org.slf4j.LoggerFactory," +
    // Apache Commons Logging
    "org.apache.commons.logging.LogFactory," +
    // Java Util Logging
    "java.util.logging.Logger," +
    // Log4J 2
    "org.apache.logging.log4j.LogManager";

  @NonNls private static final String DEFAULT_FACTORY_METHOD_NAMES =
    //Log4J 1
    "getLogger," +
    // SLF4J
    "getLogger," +
    // Apache Commons Logging
    "getLog," +
    // Java Util Logging
    "getLogger," +
    // Log4J 2
    "getLogger";
  protected final List<String> loggerFactoryClassNames = new ArrayList<>();
  protected final List<String> loggerFactoryMethodNames = new ArrayList<>();
  @SuppressWarnings("PublicField")
  public String loggerClassName = DEFAULT_FACTORY_CLASS_NAMES;
  @SuppressWarnings("PublicField")
  public @NonNls String loggerFactoryMethodName = DEFAULT_FACTORY_METHOD_NAMES;

  public boolean ignoreSuperClass = false;
  public boolean ignoreNonPublicClasses = false;

  {
    parseString(loggerClassName, loggerFactoryClassNames);
    parseString(loggerFactoryMethodName, loggerFactoryMethodNames);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      table("",
            column("loggerFactoryClassNames", InspectionGadgetsBundle.message("logger.factory.class.name"),
                       new JavaClassValidator()),
            column("loggerFactoryMethodNames", InspectionGadgetsBundle.message("logger.factory.method.name"),
                       new JavaIdentifierValidator())),
      checkbox("ignoreSuperClass", InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.ignore.super.class.option")),
      checkbox("ignoreNonPublicClasses",
               InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.ignore.non.public.classes.option"))
    );
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
      parseString(DEFAULT_FACTORY_CLASS_NAMES, loggerFactoryClassNames);
      parseString(DEFAULT_FACTORY_METHOD_NAMES, loggerFactoryMethodNames);
    }
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerFactoryClassNames);
    loggerFactoryMethodName = formatString(loggerFactoryMethodNames);
    if (loggerFactoryMethodName.equals(DEFAULT_FACTORY_METHOD_NAMES) && loggerClassName.equals(DEFAULT_FACTORY_CLASS_NAMES)) {
      // to prevent changing inspection profile with new default, which is mistakenly always written because of bug in serialization below.
      loggerFactoryMethodName = "getLogger," +
                                "getLogger," +
                                "getLog," +
                                "getLogger";
      // these broken settings are restored correctly in readSettings()
    }
    XmlSerializer.serializeInto(this, element, new SerializationFilterBase() {
      @Override
      protected boolean accepts(@NotNull Accessor accessor, @NotNull Object bean, @Nullable Object beanValue) {
        final @NonNls String factoryName = accessor.getName();
        if ("loggerClassName".equals(factoryName) && DEFAULT_FACTORY_CLASS_NAMES.equals(beanValue)) return false;
        if ("loggerFactoryMethodNames".equals(factoryName) && DEFAULT_FACTORY_METHOD_NAMES.equals(beanValue)) return false;
        if ("ignoreSuperClass".equals(factoryName) && !ignoreSuperClass) return false;
        if ("ignoreNonPublicClasses".equals(factoryName) && !ignoreNonPublicClasses) return false;
        return true;
      }
    });
  }

  private static final class LoggerInitializedWithForeignClassFix extends InspectionGadgetsFix {

    private final String newClassName;

    private LoggerInitializedWithForeignClassFix(String newClassName) {
      this.newClassName = newClassName;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", newClassName+".class");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("logger.initialized.with.foreign.class.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiClassObjectAccessExpression classObjectAccessExpression)) {
        return;
      }
      PsiReplacementUtil.replaceExpression(classObjectAccessExpression, newClassName + ".class", new CommentTracker());
    }
  }

  private class LoggerInitializedWithForeignClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
      super.visitClassObjectAccessExpression(expression);
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiReferenceExpression referenceExpression) {
        if (!expression.equals(referenceExpression.getQualifierExpression())) {
          return;
        }
        @NonNls final String name = referenceExpression.getReferenceName();
        if (!"getName".equals(name)) {
          return;
        }
        final PsiElement grandParent = referenceExpression.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
          return;
        }
        final PsiExpressionList list = methodCallExpression.getArgumentList();
        if (!list.isEmpty()) {
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
      if (ignoreNonPublicClasses && !containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
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
      final PsiClass initializerClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
      if (initializerClass == null) {
        return;
      }
      if (containingClass.equals(initializerClass)) {
        return;
      }
      if (ignoreSuperClass && containingClass.isInheritor(initializerClass, true) ||
          PsiTreeUtil.isAncestor(initializerClass, containingClass, true)) {
        if (isOnTheFly()) {
          registerError(expression, ProblemHighlightType.INFORMATION, containingClassName);
        }
        return;
      }
      registerError(expression, containingClassName);
    }
  }
}
