/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.classmetrics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class CouplingVisitor extends JavaRecursiveElementWalkingVisitor {
  private int m_inClass;
  private final PsiClass m_class;
  private final boolean m_includeJavaClasses;
  private final boolean m_includeLibraryClasses;
  private final Set<String> m_dependencies = new HashSet<>(10);

  CouplingVisitor(PsiClass aClass, boolean includeJavaClasses,
                  boolean includeLibraryClasses) {
    m_class = aClass;
    m_includeJavaClasses = includeJavaClasses;
    m_includeLibraryClasses = includeLibraryClasses;
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    super.visitField(field);
    final PsiType type = field.getType();
    addDependency(type);
  }

  @Override
  public void visitLocalVariable(@NotNull PsiLocalVariable var) {
    super.visitLocalVariable(var);
    final PsiType type = var.getType();
    addDependency(type);
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    super.visitMethod(method);
    final PsiType returnType = method.getReturnType();
    addDependency(returnType);
    addDependenciesForParameters(method);
    addDependenciesForThrowsList(method);
  }

  private void addDependenciesForThrowsList(PsiMethod method) {
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] throwsTypes = throwsList.getReferencedTypes();
    for (PsiClassType throwsType : throwsTypes) {
      addDependency(throwsType);
    }
  }

  private void addDependenciesForParameters(PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final PsiType parameterType = parameter.getType();
      addDependency(parameterType);
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression exp) {
    super.visitNewExpression(exp);
    final PsiType classType = exp.getType();
    addDependency(classType);
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression exp) {
    super.visitClassObjectAccessExpression(exp);
    final PsiTypeElement operand = exp.getOperand();
    final PsiType classType = operand.getType();
    addDependency(classType);
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    final int wasInClass = m_inClass++;
    if (wasInClass == 0) {
      super.visitClass(aClass);
    }
    final PsiType[] superTypes = aClass.getSuperTypes();
    for (PsiType superType : superTypes) {
      addDependency(superType);
    }
  }

  @Override
  protected void elementFinished(@NotNull PsiElement element) {
    super.elementFinished(element);
    if (element instanceof PsiClass) {
      m_inClass--;
    }
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    super.visitTryStatement(statement);
    final PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
    for (PsiParameter catchBlockParameter : catchBlockParameters) {
      final PsiType catchType = catchBlockParameter.getType();
      addDependency(catchType);
    }
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression exp) {
    super.visitInstanceOfExpression(exp);
    final PsiTypeElement checkType = exp.getCheckType();
    if (checkType == null) {
      return;
    }
    final PsiType classType = checkType.getType();
    addDependency(classType);
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression exp) {
    super.visitTypeCastExpression(exp);
    final PsiTypeElement castType = exp.getCastType();
    if (castType == null) {
      return;
    }
    final PsiType classType = castType.getType();
    addDependency(classType);
  }

  private void addDependency(PsiType type) {
    if (type == null) {
      return;
    }
    final PsiType baseType = type.getDeepComponentType();

    if (ClassUtils.isPrimitive(type)) {
      return;
    }
    final String qualifiedName = m_class.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    if (baseType.equalsToText(qualifiedName)) {
      return;
    }
    final String baseTypeName = baseType.getCanonicalText();
    @NonNls final String javaPrefix = "java.";
    @NonNls final String javaxPrefix = "javax.";
    if (!m_includeJavaClasses &&
        (baseTypeName.startsWith(javaPrefix) ||
         baseTypeName.startsWith(javaxPrefix))) {
      return;
    }
    if (!m_includeLibraryClasses) {
      final Project project = m_class.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
      final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(baseTypeName, searchScope);
      if (aClass == null) {
        return;
      }
      if (LibraryUtil.classIsInLibrary(aClass)) {
        return;
      }
    }
    if (StringUtil.startsWithConcatenation(baseTypeName, qualifiedName, ".")) {
      return;
    }
    m_dependencies.add(baseTypeName);
  }

  int getNumDependencies() {
    return m_dependencies.size();
  }
}
