/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;

import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public interface ClassItemGenerator {
  void writeEnumConstant(StringBuilder text, GrEnumConstant constant);
  void writeConstructor(StringBuilder text, PsiMethod constructor, boolean isEnum);
  void writeMethod(StringBuilder text, PsiMethod method);
  void writeVariableDeclarations(StringBuilder text, GrVariableDeclaration variableDeclaration);
  void writeExtendsList(StringBuilder text, PsiClass definition);
  void writeImplementsList(StringBuilder text, PsiClass definition);

  Collection<PsiMethod> collectMethods(PsiClass typeDefinition);

  boolean generateAnnotations();

  void writePostponed(StringBuilder text, PsiClass psiClass);
}
