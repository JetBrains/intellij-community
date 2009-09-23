/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovySingletonAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.04.2009
 */
public class SingletonAnnotationProcessor implements NonCodeMembersProcessor {
  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    if (!(type instanceof PsiClassType)) return true;

    PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return true;

    if (!(psiClass instanceof GrTypeDefinition)) return true;

    GrTypeDefinition grClass = (GrTypeDefinition)psiClass;

    PsiModifierList modifierList = grClass.getModifierList();
    if (modifierList==null) return true;
    assert modifierList instanceof GrModifierList;

    GrAnnotation[] annotations = ((GrModifierList)modifierList).getAnnotations();

    for (GrAnnotation annotation : annotations) {
      if (!GroovySingletonAnnotationInspection.SINGLETON.equals(annotation.getQualifiedName())) continue;
      String name = grClass.getName();
      if (name == null) return true;

      GrVariableDeclaration variableDeclaration = GroovyPsiElementFactory.getInstance(annotation.getProject())
        .createFieldDeclaration(new String[]{PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL}, "instance", null, type);
      
      GrVariable[] variables = variableDeclaration.getVariables();
      assert variables.length == 1;
      GrVariable instanceField = variables[0];
      assert instanceField instanceof GrField;

      GroovyResolveResultImpl fieldResult = new GroovyResolveResultImpl(instanceField, true);
      GroovyResolveResultImpl getterResult = new GroovyResolveResultImpl(new GrAccessorMethodImpl(((GrField)instanceField), false, "getInstance"), true);

      if (!processor.execute(fieldResult.getElement(), ResolveState.initial())) return false;
      if (!processor.execute(getterResult.getElement(), ResolveState.initial())) return false;

      return true;
    }

    return true;
  }
}
