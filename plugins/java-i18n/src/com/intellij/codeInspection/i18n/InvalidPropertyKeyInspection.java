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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class InvalidPropertyKeyInspection extends BaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.PROPERTIES_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.unresolved.property.key.reference.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "UnresolvedPropertyKey";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
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
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    appendProblems(manager, isOnTheFly, result, field.getInitializer());
    appendProblems(manager, isOnTheFly, result, field.getModifierList());
    if (field instanceof PsiEnumConstant) {
      appendProblems(manager, isOnTheFly, result, ((PsiEnumConstant)field).getArgumentList());
    }
    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  private static void appendProblems(InspectionManager manager, boolean isOnTheFly, List<ProblemDescriptor> result, PsiElement element) {
    if (element != null){
      final ProblemDescriptor[] descriptors = checkElement(element, manager, isOnTheFly);
      if (descriptors != null) {
        Collections.addAll(result, descriptors);
      }
    }
  }

  @Nullable
  private static ProblemDescriptor[] checkElement(PsiElement element, final InspectionManager manager, boolean onTheFly) {
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
    for (FileCheckingInspection obj : fileCheckingInspections) {
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

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argList = aClass.getArgumentList();
      if (argList != null) {
        argList.accept(this);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
    }

    @Override
    public void visitField(PsiField field) {
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String key = (String)value;
      if (isComputablePropertyExpression(expression)) return;
      Ref<String> resourceBundleName = new Ref<String>();
      if (!JavaI18nUtil.isValidPropertyReference(myManager.getProject(), expression, key, resourceBundleName)) {
        String bundleName = resourceBundleName.get();
        if (bundleName != null) { // can be null if we were unable to resolve literal expression, e.g. when JDK was not set
          appendPropertyKeyNotFoundProblem(bundleName, key, expression, myManager, myProblems, onTheFly);
        }
      }
      else if (expression.getParent() instanceof PsiNameValuePair) {
        PsiNameValuePair nvp = (PsiNameValuePair)expression.getParent();
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
      }
      else if (expression.getParent() instanceof PsiExpressionList && expression.getParent().getParent() instanceof PsiMethodCallExpression) {
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        if (!JavaI18nUtil.mustBePropertyKey(myManager.getProject(), expression, annotationParams)) return;

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
                && method.getParameterList().getParametersCount() == i + 2
                && method.getParameterList().getParameters()[i + 1].isVarArgs()
                && !hasArrayTypeAt(i + 1, methodCall)) {
              myProblems.add(myManager.createProblemDescriptor(methodCall,
                                                               CodeInsightBundle.message("property.has.more.parameters.than.passed", key, paramsCount, args.length - i - 1),
                                                               onTheFly, new LocalQuickFix[0],
                                                               ProblemHighlightType.GENERIC_ERROR));
            }
            break;
          }
        }
      }
    }

    private static void appendPropertyKeyNotFoundProblem(@NotNull String bundleName,
                                                         @NotNull String key,
                                                         @NotNull PsiLiteralExpression expression,
                                                         @NotNull InspectionManager manager,
                                                         @NotNull List<ProblemDescriptor> problems,
                                                         boolean onTheFly) {
      final String description = CodeInsightBundle.message("inspection.unresolved.property.key.reference.message", key);
      final List<PropertiesFile> propertiesFiles = filterNotInLibrary(expression.getProject(), JavaI18nUtil.propertiesFilesByBundleName(bundleName, expression));
      problems.add(
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
                                                           @NotNull List<PropertiesFile> propertiesFiles) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

      final List<PropertiesFile> result = new ArrayList<PropertiesFile>(propertiesFiles.size());
      for (final PropertiesFile file : propertiesFiles) {
        if (!fileIndex.isInLibraryClasses(file.getVirtualFile()) && !fileIndex.isInLibrarySource(file.getVirtualFile())) {
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

    private static boolean isComputablePropertyExpression(PsiExpression expression) {
      while (expression != null && expression.getParent() instanceof PsiParenthesizedExpression) expression = (PsiExpression)expression.getParent();
      return expression != null && expression.getParent() instanceof PsiExpression;
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }
}
