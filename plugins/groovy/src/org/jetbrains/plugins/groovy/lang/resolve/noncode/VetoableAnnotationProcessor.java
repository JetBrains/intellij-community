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

package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.05.2009
 */
public class VetoableAnnotationProcessor implements NonCodeMembersProcessor {
  public static final String VETOABLE = "groovy.beans.Vetoable";

  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    if (!(type instanceof PsiClassType)) return true;

    PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return true;

    if (!(psiClass instanceof GrTypeDefinition)) return true;
    GrTypeDefinition grClass = (GrTypeDefinition)psiClass;

    GrField[] fields = grClass.getFields();

    for (GrField field : fields) {
      GrModifierList modifierList = field.getModifierList();
      if (modifierList == null) return true;

      GrAnnotation[] annotations = modifierList.getAnnotations();
      for (GrAnnotation annotation : annotations) {
        if (VETOABLE.equals(annotation.getQualifiedName())) {
          Project project = annotation.getProject();
          GrVariableDeclaration vetoableChangeVar  = GroovyPsiElementFactory.getInstance(project)
            .createVariableDeclaration(new String[]{GrModifier.DEF}, null, PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(
              project)), "vetoableChange");

          GroovyResolveResultImpl vetoableChangeResult  = new GroovyResolveResultImpl(vetoableChangeVar.getVariables()[0], true);
          if (!processor.execute(vetoableChangeResult.getElement(), ResolveState.initial())) return false;
          break;
        }
      }
    }
    return true;
  }
}