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

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticConstructor;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 27.04.2009
 */
public class ImmutableAnnotationProcessor implements NonCodeMembersProcessor {

  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    if (!(type instanceof PsiClassType)) return true;

    PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return true;

    if (!(psiClass instanceof GrTypeDefinition)) return true;

    GrTypeDefinition grClass = (GrTypeDefinition)psiClass;

    PsiModifierList modifierList = grClass.getModifierList();
    if (modifierList == null) return true;
    assert modifierList instanceof GrModifierList;

    GrAnnotation[] annotations = ((GrModifierList)modifierList).getAnnotations();
    GrField[] fields = grClass.getFields();

    List<String> paramTypes = new ArrayList<String>();
    List<String> paramNames = new ArrayList<String>();
    for (GrField field : fields) {
      paramTypes.add(field.getType().getCanonicalText());
      paramNames.add(field.getName());
    }

    for (GrAnnotation annotation : annotations) {
      if (!GroovyImmutableAnnotationInspection.IMMUTABLE.equals(annotation.getQualifiedName())) continue;

      String name = grClass.getName();
      if (name == null) return true;
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(annotation.getProject());
      GrMethod constructor =
        factory.createConstructorFromText(name, ArrayUtil.toStringArray(paramTypes), ArrayUtil.toStringArray(paramNames), "{}");
      if (!processor.execute(new GrSyntheticConstructor(constructor, grClass), ResolveState.initial())) return false;

      constructor = factory.createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}");
      return processor.execute(new GrSyntheticConstructor(constructor, grClass), ResolveState.initial());
    }

    return true;
  }
}
