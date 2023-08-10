// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InvalidPropertyKeyInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  @NotNull
  public String getShortName() {
    return "UnresolvedPropertyKey";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkElement(method, manager, isOnTheFly);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new SmartList<>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer.getBody(), manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }

    return result.isEmpty() ? null : result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> result = new SmartList<>();
    appendProblems(manager, isOnTheFly, result, field.getInitializer());
    appendProblems(manager, isOnTheFly, result, field.getModifierList());
    if (field instanceof PsiEnumConstant) {
      appendProblems(manager, isOnTheFly, result, ((PsiEnumConstant)field).getArgumentList());
    }
    return result.isEmpty() ? null : result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static void appendProblems(InspectionManager manager, boolean isOnTheFly, List<? super ProblemDescriptor> result, PsiElement element) {
    if (element != null){
      final ProblemDescriptor[] descriptors = checkElement(element, manager, isOnTheFly);
      if (descriptors != null) {
        Collections.addAll(result, descriptors);
      }
    }
  }

  private static ProblemDescriptor @Nullable [] checkElement(PsiElement element, final InspectionManager manager, boolean onTheFly) {
    UnresolvedPropertyVisitor visitor = new UnresolvedPropertyVisitor(manager, onTheFly);
    element.accept(visitor);
    Map<PsiElement, ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.values().toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static class UnresolvedPropertyVisitor extends JavaRecursiveElementWalkingVisitor {
    private final InspectionManager myManager;
    private final Map<PsiElement, ProblemDescriptor> myProblems = new HashMap<>();
    private final boolean onTheFly;


    UnresolvedPropertyVisitor(final InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      this.onTheFly = onTheFly;
    }

    @Override
    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
      final PsiExpressionList argList = aClass.getArgumentList();
      if (argList != null) {
        argList.accept(this);
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
    }

    @Override
    public void visitField(@NotNull PsiField field) {
    }

    @Override
    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (isComputedPropertyExpression(expression)) {
        return;
      }
      final PsiElement resolvedExpression = expression.resolve();
      if (resolvedExpression instanceof PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
        PsiExpression initializer;
        PsiExpression highlightedExpression;
        if (field.getContainingFile() == expression.getContainingFile()) {
          highlightedExpression = initializer = PsiUtil.skipParenthesizedExprDown(field.getInitializer());
        } else {
          initializer = PsiUtil.skipParenthesizedExprDown(PsiFieldImpl.getDetachedInitializer(field));
          highlightedExpression = expression;
        }
        String key = computeStringValue(initializer);
        visitPropertyKeyAnnotationParameter(expression, key, highlightedExpression);
      }
      else if (resolvedExpression instanceof PsiLocalVariable) {
        checkLocalVariable((PsiLocalVariable)resolvedExpression, expression);
      }
    }

    private void checkLocalVariable(@NotNull PsiLocalVariable variable, PsiReferenceExpression expression) {
      PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (block == null) return;
      final PsiElement[] defs = DefUseUtil.getDefs(block, variable, expression);
      for (PsiElement def : defs) {
        if(def instanceof PsiLocalVariable) {
          final PsiExpression initializer = PsiUtil.deparenthesizeExpression(((PsiLocalVariable)def).getInitializer());
          visitPropertyKeyAnnotationParameter(expression, computeStringValue(initializer), initializer);
        }
        else if (def instanceof PsiReferenceExpression) {
          final PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(def.getParent());
          if (assignment != null && assignment.getLExpression() == def) {
            final PsiExpression rhs = PsiUtil.deparenthesizeExpression(assignment.getRExpression());
            if (rhs instanceof PsiConditionalExpression conditionalExpression) {
              final PsiExpression thenExpression = conditionalExpression.getThenExpression();
              final PsiExpression elseExpression = conditionalExpression.getElseExpression();
              visitPropertyKeyAnnotationParameter(expression, computeStringValue(thenExpression), thenExpression);
              visitPropertyKeyAnnotationParameter(expression, computeStringValue(elseExpression), elseExpression);
            }
            else {
              visitPropertyKeyAnnotationParameter(expression, computeStringValue(rhs), rhs);
            }
          }
        }
      }
    }

    private static String computeStringValue(PsiExpression expression) {
      if (expression instanceof PsiLiteralExpression) {
        final Object value = ((PsiLiteralExpression)expression).getValue();
        if (value instanceof String) {
          return (String)value;
        }
      }
      return null;
    }

    private void visitPropertyKeyAnnotationParameter(PsiExpression expression, String key, PsiExpression highlightedExpression) {
      if (key == null) return;
      Ref<String> resourceBundleName = new Ref<>();
      if (!JavaI18nUtil.isValidPropertyReference(myManager.getProject(), expression, key, resourceBundleName)) {
        String bundleName = resourceBundleName.get();
        if (bundleName != null) { // can be null if we were unable to resolve literal expression, e.g. when JDK was not set
          appendPropertyKeyNotFoundProblem(bundleName, key, highlightedExpression, myManager, myProblems, onTheFly);
        }
      }
      else if (expression.getParent() instanceof PsiNameValuePair nvp) {
        if (Objects.equals(nvp.getName(), AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
          PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(expression.getProject());
          Module module = ModuleUtilCore.findModuleForPsiElement(expression);
          if (module != null) {
            List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, key);
            if (propFiles.isEmpty()) {
              final String description = JavaI18nBundle.message("inspection.invalid.resource.bundle.reference", key);
              final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                                  description,
                                                                                  (LocalQuickFix)null,
                                                                                  ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
              myProblems.putIfAbsent(expression, problem);
            }
          }
        }
      }
      else if (expression.getParent() instanceof PsiExpressionList expressions && expression.getParent().getParent() instanceof PsiMethodCallExpression) {
        if (!JavaI18nUtil.mustBePropertyKey(expression, null)) return;

        final SortedSet<Integer> paramsCount = JavaI18nUtil.getPropertyValueParamsCount(highlightedExpression, resourceBundleName.get());
        if (paramsCount.isEmpty() || paramsCount.size() != 1 && resourceBundleName.get() == null) {
          return;
        }

        final int maxParamCount = paramsCount.last();

        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expressions.getParent();
        final PsiMethod method = methodCall.resolveMethod();
        final PsiExpression[] args = expressions.getExpressions();
        for (int i = 0; i < args.length; i++) {
          if (args[i] == expression) {
            if (i + maxParamCount >= args.length
                && method != null
                && method.getParameterList().getParametersCount() == i + 2
                && Objects.requireNonNull(method.getParameterList().getParameter(i + 1)).isVarArgs()
                && !hasArrayTypeAt(i + 1, methodCall)) {
              myProblems.putIfAbsent(methodCall, myManager.createProblemDescriptor(methodCall,
                                                                                   JavaI18nBundle.message("property.has.more.parameters.than.passed", key, maxParamCount, args.length - i - 1),
                                                                                   onTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL));
            }
            break;
          }
        }
      }
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      if (isComputedPropertyExpression(expression)) return;
      visitPropertyKeyAnnotationParameter(expression, computeStringValue(expression), expression);
    }

    private static void appendPropertyKeyNotFoundProblem(@NotNull String bundleName,
                                                         @NotNull String key,
                                                         @NotNull PsiExpression expression,
                                                         @NotNull InspectionManager manager,
                                                         @NotNull Map<PsiElement, ProblemDescriptor> problems,
                                                         boolean onTheFly) {
      final String description = JavaI18nBundle.message("inspection.unresolved.property.key.reference.message", key);
      final List<PropertiesFile> propertiesFiles = filterNotInLibrary(expression.getProject(),
                                                                      I18nUtil.propertiesFilesByBundleName(bundleName, expression));
      if (problems.containsKey(expression)) return;
      problems.put(expression,
                   manager.createProblemDescriptor(
                     expression,
                     description,
                     propertiesFiles.isEmpty() ? null : new JavaCreatePropertyFix(expression, key, propertiesFiles),
                     ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly
                   )
      );
    }

    @NotNull
    private static List<PropertiesFile> filterNotInLibrary(@NotNull Project project,
                                                           @NotNull List<? extends PropertiesFile> propertiesFiles) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

      final List<PropertiesFile> result = new ArrayList<>(propertiesFiles.size());
      for (final PropertiesFile file : propertiesFiles) {
        if (!fileIndex.isInLibrary(file.getVirtualFile())) {
          result.add(file);
        }
      }
      return result;
    }

    private static boolean hasArrayTypeAt(int i, PsiMethodCallExpression methodCall) {
      return methodCall != null
             && methodCall.getArgumentList().getExpressionTypes().length > i
             && methodCall.getArgumentList().getExpressionTypes()[i] instanceof PsiArrayType;
    }

    private static boolean isComputedPropertyExpression(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression &&
             (expression == ((PsiConditionalExpression)parent).getThenExpression() ||
              expression == ((PsiConditionalExpression)parent).getElseExpression())) {
        expression = (PsiExpression)parent;
        parent = expression.getParent();
      }
      return parent instanceof PsiExpression;
    }

    public Map<PsiElement, ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }
}
