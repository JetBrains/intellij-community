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
package com.intellij.codeInspection.i18n;

import com.intellij.ExtensionPoints;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class InvalidPropertyKeyInspection extends BaseJavaLocalInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.unresolved.property.key.reference.name");
  }

  @NotNull
  public String getShortName() {
    return "UnresolvedPropertyKey";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkElement(method, manager, isOnTheFly);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) return checkElement(initializer, manager, isOnTheFly);

    if (field instanceof PsiEnumConstant) {
      return checkElement(((PsiEnumConstant)field).getArgumentList(), manager, isOnTheFly);
    }
    return null;
  }

  @Nullable private static ProblemDescriptor[] checkElement(PsiElement element, final InspectionManager manager, boolean onTheFly) {
    UnresolvedPropertyVisitor visitor = new UnresolvedPropertyVisitor(manager, onTheFly);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, boolean isOnTheFly) {
    ExtensionPoint<FileCheckingInspection> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INVALID_PROPERTY_KEY_INSPECTION_TOOL);
    final FileCheckingInspection[] fileCheckingInspections = point.getExtensions();
    for(FileCheckingInspection obj: fileCheckingInspections) {
      ProblemDescriptor[] descriptors = obj.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  private static class UnresolvedPropertyVisitor extends JavaRecursiveElementWalkingVisitor {
    private final InspectionManager myManager;
    private final List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();
    private final boolean onTheFly;


    public UnresolvedPropertyVisitor(final InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      this.onTheFly = onTheFly;
    }

    @Override public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argList = aClass.getArgumentList();
      if (argList != null) {
        argList.accept(this);
      }
    }

    @Override public void visitClass(PsiClass aClass) {}

    @Override public void visitField(PsiField field) {}

    @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String key = (String)value;
      if (isComputablePropertyExpression(expression)) return;
      Ref<String> resourceBundleName = new Ref<String>();
      if (!JavaI18nUtil.isValidPropertyReference(expression, key, resourceBundleName)) {
        final String description = CodeInsightBundle.message("inspection.unresolved.property.key.reference.message", key);
        final String bundleName = resourceBundleName.get();
        final List<PropertiesFile> propertiesFiles = JavaI18nUtil.propertiesFilesByBundleName(bundleName, expression);
        final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                            description,
                                                                            new JavaCreatePropertyFix(expression, key, propertiesFiles),
                                                                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
        myProblems.add(problem);
      } else
      if (expression.getParent() instanceof PsiNameValuePair) {
        PsiNameValuePair nvp = (PsiNameValuePair) expression.getParent();
        if (Comparing.equal(nvp.getName(), AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
          PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(expression.getProject());
          Module module = ModuleUtil.findModuleForPsiElement(expression);
          if (module != null) {
            List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, key);
            if (propFiles.isEmpty()) {
              final String description = CodeInsightBundle.message("inspection.invalid.resource.bundle.reference", key);
              final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                                  description,
                                                                                  (LocalQuickFix)null,
                                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
              myProblems.add(problem);
            }
          }
        }
      } else
      if (expression.getParent() instanceof PsiExpressionList && expression.getParent().getParent() instanceof PsiMethodCallExpression) {
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        if (! JavaI18nUtil.mustBePropertyKey(expression, annotationParams)) return;

        final int paramsCount = JavaI18nUtil.getPropertyValueParamsMaxCount(expression);
        if (paramsCount == -1) return;

        final PsiExpressionList expressions = (PsiExpressionList)expression.getParent();
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expressions.getParent();
        final PsiMethod method = methodCall.resolveMethod();
        final PsiExpression[] args = expressions.getExpressions();
        for (int i = 0; i < args.length; i++) {
          if (args[i] == expression) {
            if (i + paramsCount >= args.length
                && method != null
                && method.getParameterList().getParametersCount() == i+2
                && method.getParameterList().getParameters()[i+1].isVarArgs()
                && !hasArrayTypeAt(i+1, methodCall)) {
              myProblems.add(myManager.createProblemDescriptor(methodCall,
                                                               CodeInsightBundle.message("property.has.more.parameters.than.passed", key, paramsCount, args.length-i-1),
                                                               onTheFly, new LocalQuickFix[0],
                                                               ProblemHighlightType.GENERIC_ERROR));
            }
            break;
          }
        }
      }
    }

    private static boolean hasArrayTypeAt(int i, PsiMethodCallExpression methodCall) {
      return methodCall != null
             && methodCall.getArgumentList().getExpressionTypes().length > i
             && methodCall.getArgumentList().getExpressionTypes()[i] instanceof PsiArrayType;
    }

    private static boolean isComputablePropertyExpression(PsiExpression expression) {
      while (expression != null && expression.getParent() instanceof PsiParenthesizedExpression) expression = (PsiExpression)expression.getParent();
      return expression != null && expression.getParent() instanceof PsiExpression;
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }
}
