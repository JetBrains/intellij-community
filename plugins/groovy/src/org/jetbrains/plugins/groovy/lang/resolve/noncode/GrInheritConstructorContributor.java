/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.AstTransformContributor;

import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class GrInheritConstructorContributor extends AstTransformContributor {
  public static final String INHERIT_CONSTRUCTOR_NAME = "groovy.transform.InheritConstructors";

  @Override
  public void collectMethods(@NotNull GrTypeDefinition psiClass, Collection<PsiMethod> collector) {
    if (psiClass.isAnonymous() || psiClass.isInterface() || psiClass.isEnum()) {
      return;
    }

    if (!hasInheritConstructorsAnnotation(psiClass)) return;

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null) return;

    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, PsiSubstitutor.EMPTY);
    for (PsiMethod constructor : superClass.getConstructors()) {
      final GrLightMethodBuilder inheritedConstructor =
        new GrLightMethodBuilder(psiClass.getManager(), psiClass.getName()).setContainingClass(psiClass);
      inheritedConstructor.setConstructor(true).setNavigationElement(psiClass);
      for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
        inheritedConstructor
          .addParameter(StringUtil.notNullize(parameter.getName()), superClassSubstitutor.substitute(parameter.getType()), false);
      }
      if (psiClass.findCodeMethodsBySignature(inheritedConstructor, false).length == 0) {
        collector.add(inheritedConstructor);
      }
    }
  }

  public static boolean hasInheritConstructorsAnnotation(PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    return modifierList != null && modifierList.findAnnotation(INHERIT_CONSTRUCTOR_NAME) != null;
  }
}
