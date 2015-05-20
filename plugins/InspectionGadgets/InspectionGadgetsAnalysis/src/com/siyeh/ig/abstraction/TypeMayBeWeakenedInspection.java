/*
 * Copyright 2006-2015 Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class TypeMayBeWeakenedInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean useRighthandTypeAsWeakestTypeInAssignments = true;

  @SuppressWarnings({"PublicField"})
  public boolean useParameterizedTypeForCollectionMethods = true;

  @SuppressWarnings({"PublicField"})
  public boolean doNotWeakenToJavaLangObject = true;

  @SuppressWarnings({"PublicField"})
  public boolean onlyWeakentoInterface = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("type.may.be.weakened.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>)infos[1];
    @NonNls final StringBuilder builder = new StringBuilder();
    final Iterator<PsiClass> iterator = weakerClasses.iterator();
    if (iterator.hasNext()) {
      builder.append('\'').append(getClassName(iterator.next())).append('\'');
      while (iterator.hasNext()) {
        builder.append(", '").append(getClassName(iterator.next())).append('\'');
      }
    }
    final Object info = infos[0];
    if (info instanceof PsiField) {
      return InspectionGadgetsBundle.message("type.may.be.weakened.field.problem.descriptor",
        builder.toString());
    }
    else if (info instanceof PsiParameter) {
      return InspectionGadgetsBundle.message("type.may.be.weakened.parameter.problem.descriptor",
        builder.toString());
    }
    else if (info instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("type.may.be.weakened.method.problem.descriptor",
        builder.toString());
    }
    return InspectionGadgetsBundle.message("type.may.be.weakened.problem.descriptor", builder.toString());
  }

  private static String getClassName(PsiClass aClass) {
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return aClass.getName();
    }
    return qualifiedName;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("type.may.be.weakened.ignore.option"),
                             "useRighthandTypeAsWeakestTypeInAssignments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("type.may.be.weakened.collection.method.option"),
                             "useParameterizedTypeForCollectionMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("type.may.be.weakened.do.not.weaken.to.object.option"),
                             "doNotWeakenToJavaLangObject");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("only.weaken.to.an.interface"),
                             "onlyWeakentoInterface");
    return optionsPanel;
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>)infos[1];
    final Collection<InspectionGadgetsFix> fixes = new ArrayList();
    for (PsiClass weakestClass : weakerClasses) {
      final String className = getClassName(weakestClass);
      if (className == null) {
        continue;
      }
      fixes.add(new TypeMayBeWeakenedFix(className));
    }
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

    private final String fqClassName;

    TypeMayBeWeakenedFix(@NotNull String fqClassName) {
      this.fqClassName = fqClassName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("type.may.be.weakened.quickfix", fqClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Weaken type";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiTypeElement typeElement;
      if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        typeElement = variable.getTypeElement();
      }
      else if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)parent;
        typeElement = method.getReturnTypeElement();
      }
      else {
        return;
      }
      if (typeElement == null) {
        return;
      }
      final PsiJavaCodeReferenceElement componentReferenceElement = typeElement.getInnermostComponentReferenceElement();
      if (componentReferenceElement == null) {
        return;
      }
      final PsiType oldType = typeElement.getType();
      if (!(oldType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType oldClassType = (PsiClassType)oldType;
      final PsiType[] parameterTypes = oldClassType.getParameters();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final PsiType type = factory.createTypeFromText(fqClassName, element);
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass != null) {
        final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (typeParameters.length != 0 && typeParameters.length == parameterTypes.length) {
          final Map<PsiTypeParameter, PsiType> typeParameterMap = new HashMap();
          for (int i = 0; i < typeParameters.length; i++) {
            final PsiTypeParameter typeParameter = typeParameters[i];
            final PsiType parameterType = parameterTypes[i];
            typeParameterMap.put(typeParameter, parameterType);
          }
          final PsiSubstitutor substitutor = factory.createSubstitutor(typeParameterMap);
          classType = factory.createType(aClass, substitutor);
        }
      }
      final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
      final PsiElement replacement = componentReferenceElement.replace(referenceElement);
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(replacement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeMayBeWeakenedVisitor();
  }

  private class TypeMayBeWeakenedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiCatchSection) {
          // do not weaken catch block parameters
          return;
        } else if (declarationScope instanceof PsiLambdaExpression && parameter.getTypeElement() == null) {
          //no need to check inferred lambda params
          return;
        }
        else if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass == null ||
              containingClass.isInterface()) {
            return;
          }
          if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) {
            return;
          }
          if (MethodUtils.hasSuper(method)) {
            // do not try to weaken parameters of methods with
            // super methods
            return;
          }
          final Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
          if (overridingSearch.findFirst() != null) {
            // do not try to weaken parameters of methods with
            // overriding methods.
            return;
          }
        }
      }
      if (isOnTheFly() && variable instanceof PsiField) {
        // checking variables with greater visibility is too expensive
        // for error checking in the editor
        if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
      }
      if (useRighthandTypeAsWeakestTypeInAssignments) {
        if (variable instanceof PsiParameter) {
          final PsiElement parent = variable.getParent();
          if (parent instanceof PsiForeachStatement) {
            final PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
            final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
            if (!(iteratedValue instanceof PsiNewExpression) && !(iteratedValue instanceof PsiTypeCastExpression)) {
              return;
            }
          }
        }
        else {
          final PsiExpression initializer = variable.getInitializer();
          if (!(initializer instanceof PsiNewExpression) && !(initializer instanceof PsiTypeCastExpression)) {
            return;
          }
        }
      }
      final Collection<PsiClass> weakestClasses =
        WeakestTypeFinder.calculateWeakestClassesNecessary(variable,
                                                           useRighthandTypeAsWeakestTypeInAssignments,
                                                           useParameterizedTypeForCollectionMethods);
      if (doNotWeakenToJavaLangObject) {
        final Project project = variable.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass javaLangObjectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, variable.getResolveScope());
        weakestClasses.remove(javaLangObjectClass);
      }
      if (onlyWeakentoInterface) {
        for (Iterator<PsiClass> iterator = weakestClasses.iterator(); iterator.hasNext(); ) {
          final PsiClass weakestClass = iterator.next();
          if (!weakestClass.isInterface()) {
            iterator.remove();
          }
        }
      }
      if (weakestClasses.isEmpty()) {
        return;
      }
      registerVariableError(variable, variable, weakestClasses);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (isOnTheFly() && !method.hasModifierProperty(PsiModifier.PRIVATE) && !ApplicationManager.getApplication().isUnitTestMode()) {
        // checking methods with greater visibility is too expensive.
        // for error checking in the editor
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        // do not try to weaken methods with super methods
        return;
      }
      final Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
      if (overridingSearch.findFirst() != null) {
        // do not try to weaken methods with overriding methods.
        return;
      }
      final Collection<PsiClass> weakestClasses =
        WeakestTypeFinder.calculateWeakestClassesNecessary(method,
                                                           useRighthandTypeAsWeakestTypeInAssignments,
                                                           useParameterizedTypeForCollectionMethods);
      if (doNotWeakenToJavaLangObject) {
        final Project project = method.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass javaLangObjectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, method.getResolveScope());
        weakestClasses.remove(javaLangObjectClass);
      }
      if (onlyWeakentoInterface) {
        for (Iterator<PsiClass> iterator = weakestClasses.iterator(); iterator.hasNext(); ) {
          final PsiClass weakestClass = iterator.next();
          if (!weakestClass.isInterface()) {
            iterator.remove();
          }
        }
      }
      if (weakestClasses.isEmpty()) {
        return;
      }
      registerMethodError(method, method, weakestClasses);
    }
  }
}