/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  @Nullable
  @Override
  public GrAccessorMethod getSetter() {
    return null;
  }

  @NotNull
  @Override
  public GrAccessorMethod[] getGetters() {
    return GrAccessorMethod.EMPTY_ARRAY;
  }

  @NotNull
  public GrVariable getOriginalVariable() {
    return (GrVariable)getNavigationElement();
  }
}
