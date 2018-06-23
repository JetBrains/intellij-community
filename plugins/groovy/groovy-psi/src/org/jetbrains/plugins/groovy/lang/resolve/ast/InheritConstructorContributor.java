// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

/**
 * @author Maxim.Medvedev
 */
public class InheritConstructorContributor implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    GrTypeDefinition psiClass = context.getCodeClass();
    if (psiClass.isAnonymous() || psiClass.isInterface() || psiClass.isEnum()) {
      return;
    }

    if (!hasInheritConstructorsAnnotation(psiClass)) return;

    final PsiClass superClass = context.getSuperClass();
    if (superClass == null) return;

    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, PsiSubstitutor.EMPTY);
    for (PsiMethod constructor : superClass.getConstructors()) {
      if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) continue;

      final GrLightMethodBuilder inheritedConstructor = new GrLightMethodBuilder(context.getManager(), context.getClassName());
      inheritedConstructor.setConstructor(true);
      inheritedConstructor.setNavigationElement(psiClass);
      inheritedConstructor.addModifier(VisibilityUtil.getVisibilityModifier(constructor.getModifierList()));
      inheritedConstructor.setOriginInfo("created by @InheritConstructors");

      for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
        String name = StringUtil.notNullize(parameter.getName());
        PsiType type = superClassSubstitutor.substitute(parameter.getType());
        inheritedConstructor.addParameter(name, type, false);
      }
      context.addMethod(inheritedConstructor);
    }
  }

  public static boolean hasInheritConstructorsAnnotation(PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    return modifierList != null && modifierList.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_INHERIT_CONSTRUCTORS);
  }
}
