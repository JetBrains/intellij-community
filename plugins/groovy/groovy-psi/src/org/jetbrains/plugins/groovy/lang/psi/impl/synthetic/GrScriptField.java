// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class GrScriptField extends GrLightField {
  public static final GrScriptField[] EMPTY_ARRAY = new GrScriptField[0];

  public GrScriptField(@NotNull GrVariable original, @NotNull GroovyScriptClass scriptClass) {
    super(scriptClass, original.getName(), original.getType(), original);

    final GrLightModifierList modifierList = getModifierList();
    for (@PsiModifier.ModifierConstant String modifier : PsiModifier.MODIFIERS) {
      if (original.hasModifierProperty(modifier)) {
        modifierList.addModifier(modifier);
      }
    }

    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      final String qname = annotation.getQualifiedName();
      final String annotationName = qname != null ? qname : annotation.getShortName();
      if (!GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(annotationName)) {
        modifierList.addAnnotation(annotationName);
      }
    }
  }

  @Override
  public @Nullable GrAccessorMethod getSetter() {
    return null;
  }

  @Override
  public GrAccessorMethod @NotNull [] getGetters() {
    return GrAccessorMethod.EMPTY_ARRAY;
  }

  public @NotNull GrVariable getOriginalVariable() {
    return (GrVariable)getNavigationElement();
  }
}
