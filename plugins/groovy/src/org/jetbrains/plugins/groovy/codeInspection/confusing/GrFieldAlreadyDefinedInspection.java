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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyAnnotator;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrFieldAlreadyDefinedInspection extends BaseInspection {
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }


  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }


  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("field.already.defined", args);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("field.already.defined");
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitVariable(GrVariable variable) {
      super.visitVariable(variable);
      if (variable instanceof GrField) return;

      GroovyPsiElement duplicate = ResolveUtil
        .resolveExistingElement(variable, new GroovyAnnotator.DuplicateVariablesProcessor(variable), GrVariable.class,
                                GrReferenceExpression.class);
      if (duplicate == null && variable instanceof GrParameter) {
        final PsiElement parent = variable.getParent().getParent();
        if (parent instanceof GrClosableBlock) {
          duplicate = ResolveUtil
            .resolveExistingElement((GrClosableBlock)parent, new GroovyAnnotator.DuplicateVariablesProcessor(variable), GrVariable.class,
                                    GrReferenceExpression.class);
        }
      }

      if (duplicate instanceof GrField) {
        registerError(variable.getNameIdentifierGroovy(), variable.getName());
      }
    }
  }
}
