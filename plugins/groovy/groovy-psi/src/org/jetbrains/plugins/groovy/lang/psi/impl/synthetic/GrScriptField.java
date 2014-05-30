/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrScriptField extends GrLightField {
  public static final GrScriptField[] EMPTY_ARRAY = new GrScriptField[0];

  private GrScriptField(@NotNull GrVariable original, @NotNull GroovyScriptClass scriptClass) {
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
  public static GrScriptField getScriptField(@NotNull final GrVariable original) {
    final GroovyScriptClass script = (GroovyScriptClass)((GroovyFile)original.getContainingFile()).getScriptClass();
    assert script != null;

    final GrScriptField result = ContainerUtil.find(getScriptFields(script), new Condition<GrScriptField>() {
      @Override
      public boolean value(GrScriptField field) {
        return field.getNavigationElement() == original;
      }
    });
    assert result != null;

    return result;
  }

  @NotNull
  public static GrScriptField[] getScriptFields(@NotNull final GroovyScriptClass script) {
    return CachedValuesManager.getCachedValue(script, new CachedValueProvider<GrScriptField[]>() {
      @Override
      public Result<GrScriptField[]> compute() {
        List<GrScriptField> result = RecursionManager.doPreventingRecursion(script, true, new Computable<List<GrScriptField>>() {
          @Override
          public List<GrScriptField> compute() {
            final List<GrScriptField> result = new ArrayList<GrScriptField>();
            script.getContainingFile().accept(new GroovyRecursiveElementVisitor() {
              @Override
              public void visitVariableDeclaration(GrVariableDeclaration element) {
                if (element.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD) != null) {
                  for (GrVariable variable : element.getVariables()) {
                    result.add(new GrScriptField(variable, script));
                  }
                }
                super.visitVariableDeclaration(element);
              }

              @Override
              public void visitMethod(GrMethod method) {
                //skip methods
              }

              @Override
              public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
                //skip type defs
              }


            });
            return result;
          }
        });

        if (result == null) {
          return Result.create(EMPTY_ARRAY, script.getContainingFile());
        }
        else {
          return Result.create(result.toArray(new GrScriptField[result.size()]), script.getContainingFile());
        }
      }
    });
  }

  @NotNull
  public GrVariable getOriginalVariable() {
    return (GrVariable)getNavigationElement();
  }
}
