/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.macro.MacroUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author max
 */
public class JavaI18nUtil extends I18nUtil {
  public static final PropertyCreationHandler DEFAULT_PROPERTY_CREATION_HANDLER = new PropertyCreationHandler() {
    public void createProperty(final Project project, final Collection<PropertiesFile> propertiesFiles, final String key, final String value,
                               final PsiExpression[] parameters) throws IncorrectOperationException {
      JavaI18nUtil.createProperty(project, propertiesFiles, key, value);
    }
  };

  private JavaI18nUtil() {
  }

  public static boolean mustBePropertyKey(final PsiLiteralExpression expression, @NotNull Map<String, Object> annotationAttributeValues) {
    return isPassedToAnnotatedParam(expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null);
  }

  public static boolean isPassedToAnnotatedParam(PsiExpression expression,
                                                 final String annFqn,
                                                 @NotNull Map<String, Object> annotationAttributeValues,
                                                 @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = getToplevelExpression(expression);
    final PsiElement parent = expression.getParent();

    if (!(parent instanceof PsiExpressionList)) return false;
    int idx = -1;
    final PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      if (PsiTreeUtil.isAncestor(arg, expression, false)) {
        idx = i;
        break;
      }
    }
    if (idx == -1) return false;

    PsiElement grParent = parent.getParent();

    if (grParent instanceof PsiAnonymousClass) {
      grParent = grParent.getParent();
    }

    if (grParent instanceof PsiCall) {
      PsiMethod method = ((PsiCall)grParent).resolveMethod();
      if (method != null &&
          isMethodParameterAnnotatedWith(method, idx, new THashSet<PsiMethod>(), annFqn, annotationAttributeValues, nonNlsTargets)) {
        return true;
      }
    }

    return false;
  }

  public static PsiExpression getToplevelExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression) {
      final PsiExpression parent = (PsiExpression)expression.getParent();
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getCondition() == expression) break;
      expression = parent;
      if (expression instanceof PsiAssignmentExpression) break;
    }
    return expression;
  }

  public static boolean isMethodParameterAnnotatedWith(final PsiMethod method,
                                                       final int idx,
                                                       Collection<PsiMethod> processed,
                                                       final String annFqn,
                                                       @NotNull Map<String, Object> annotationAttributeValues,
                                                       @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    if (processed.contains(method)) return false;
    processed.add(method);


    final PsiParameter[] params = method.getParameterList().getParameters();
    PsiParameter param;
    if (idx >= params.length) {
      if (params.length == 0) {
        return false;
      }
      PsiParameter lastParam = params [params.length-1];
      if (lastParam.isVarArgs()) {
        param = lastParam;
      }
      else {
        return false;
      }
    }
    else {
      param = params[idx];
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(param, annFqn);
    if (annotation != null) {
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final String name = attribute.getName();
        if (annotationAttributeValues.containsKey(name)) {
          annotationAttributeValues.put(name, attribute.getValue());
        }
      }
      return true;
    }
    if (nonNlsTargets != null) {
      nonNlsTargets.add(param);
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isMethodParameterAnnotatedWith(superMethod, idx, processed, annFqn, annotationAttributeValues, null)) return true;
    }

    return false;
  }

  public static boolean isValidPropertyReference(PsiLiteralExpression expression, final String key, Ref<String> outResourceBundle) {
    final HashMap<String, Object> annotationAttributeValues = new HashMap<String, Object>();
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    if (mustBePropertyKey(expression, annotationAttributeValues)) {
      final Object resourceBundleName = annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
      if (!(resourceBundleName instanceof PsiExpression)) {
        return false;
      }
      PsiExpression expr = (PsiExpression)resourceBundleName;
      final Object value = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
      if (value == null) {
        return false;
      }
      String bundleName = value.toString();
      outResourceBundle.set(bundleName);
      return isPropertyRef(expression, key, bundleName);
    }
    return true;
  }

  public static boolean isPropertyRef(final PsiLiteralExpression expression, final String key, final String resourceBundleName) {
    if (resourceBundleName == null) {
      return !PropertiesUtil.findPropertiesByKey(expression.getProject(), key).isEmpty();
    }
    else {
      final List<PropertiesFile> propertiesFiles = propertiesFilesByBundleName(resourceBundleName, expression);
      boolean containedInPropertiesFile = false;
      for (PropertiesFile propertiesFile : propertiesFiles) {
        containedInPropertiesFile |= propertiesFile.findPropertyByKey(key) != null;
      }
      return containedInPropertiesFile;
    }
  }

  public static Set<String> suggestExpressionOfType(final PsiClassType type, final PsiLiteralExpression context) {
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(context, "");
    Set<String> result = new LinkedHashSet<String>();
    for (PsiVariable var : variables) {
      PsiType varType = var.getType();
      if (type == null || type.isAssignableFrom(varType)) {
        result.add(var.getNameIdentifier().getText());
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(context, type);
    for (PsiExpression expression : expressions) {
      result.add(expression.getText());
    }
    if (type != null) {
      addAvailableMethodsOfType(type, context, result);
    }
    return result;
  }

  private static void addAvailableMethodsOfType(final PsiClassType type, final PsiLiteralExpression context, final Collection<String> result) {
    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiType returnType = method.getReturnType();
          if (returnType != null && TypeConversionUtil.isAssignable(type, returnType)
              && method.getParameterList().getParametersCount() == 0) {
            result.add(method.getName() + "()");
          }
        }
        return true;
      }

      public <T> T getHint(Key<T> hintKey) {
        return null;
      }

      public void handleEvent(Event event, Object associated) {

      }
    }, context, null);
  }

  /**
   * Returns number of different parameters in i18n message. For example, for string
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
   * number of parameters is 3.
   *
   * @param expression i18n literal
   * @return number of parameters
   */
  public static int getPropertyValueParamsMaxCount(final PsiLiteralExpression expression) {
    int maxCount = -1;
    for (PsiReference reference : expression.getReferences()) {
      if (reference instanceof PsiPolyVariantReference) {
        for (ResolveResult result : ((PsiPolyVariantReference)reference).multiResolve(false)) {
          if (result.isValidResult() && result.getElement() instanceof Property) {
            String value = ((Property)result.getElement()).getValue();
            MessageFormat format;
            try {
              format = new MessageFormat(value);
            }
            catch (Exception e) {
              continue; // ignore syntax error
            }
            try {
              int count = format.getFormatsByArgumentIndex().length;
              maxCount = Math.max(maxCount, count);
            }
            catch (IllegalArgumentException ignored) {
            }
          }
        }
      }
    }
    return maxCount;
  }
}
