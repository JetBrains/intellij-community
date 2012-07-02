/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class GrScriptField extends GrLightField {
  private GrScriptField(GrVariable original, GroovyScriptClass scriptClass) {
    super(scriptClass, original.getName(), original.getType(), original);

    mySetterInitialized = true;
    mySetter = null;
    myGetters = GrAccessorMethod.EMPTY_ARRAY;

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


  private static final Key<CachedValue<GrScriptField>> KEY = Key.create("script field");

  public static GrScriptField createScriptFieldFrom(final GrVariable original) {
    CachedValue<GrScriptField> data = original.getUserData(KEY);
    if (data == null) {
      data = CachedValuesManager.getManager(original.getProject()).createCachedValue(new CachedValueProvider<GrScriptField>() {
        @Override
        public Result<GrScriptField> compute() {
          final GroovyScriptClass script = (GroovyScriptClass)((GroovyFile)original.getContainingFile()).getScriptClass();
          assert script != null;
          return Result.create(new GrScriptField(original, script), original);
        }
      });
    }
    return data.getValue();
  }

  public GrVariable getOriginalVariable() {
    return (GrVariable)getNavigationElement();
  }
}
