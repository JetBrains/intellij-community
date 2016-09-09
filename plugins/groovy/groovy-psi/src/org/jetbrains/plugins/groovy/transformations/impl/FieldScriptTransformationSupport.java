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
package org.jetbrains.plugins.groovy.transformations.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

public class FieldScriptTransformationSupport implements AstTransformationSupport {
  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (!(context.getCodeClass() instanceof GroovyScriptClass)) return;
    final GroovyScriptClass scriptClass = (GroovyScriptClass)context.getCodeClass();
    scriptClass.getContainingFile().accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration element) {
        if (element.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD) != null) {
          for (GrVariable variable : element.getVariables()) {
            context.addField(new GrScriptField(variable, scriptClass));
          }
        }
        super.visitVariableDeclaration(element);
      }

      @Override
      public void visitMethod(@NotNull GrMethod method) {
        //skip methods
      }

      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
        //skip type defs
      }
    });
  }
}
