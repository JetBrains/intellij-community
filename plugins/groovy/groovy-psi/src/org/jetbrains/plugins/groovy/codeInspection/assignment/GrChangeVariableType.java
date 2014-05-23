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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Max Medvedev
 */
public class GrChangeVariableType extends GroovyFix {
  private static final Logger LOG = Logger.getInstance(GrChangeVariableType.class);
  private final String myType;
  private final String myName;

  public GrChangeVariableType(PsiType type, String name) {
    myType = type.getCanonicalText();
    myName = name;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();

    try {
      final PsiType type = JavaPsiFacade.getElementFactory(project).createTypeFromText(myType, element);

      if (parent instanceof GrVariable) {
        ((GrVariable)parent).setType(type);
      }
      else if (element instanceof GrReferenceExpression &&
               parent instanceof GrAssignmentExpression &&
               ((GrAssignmentExpression)parent).getLValue() == element) {
        final PsiElement resolved = ((GrReferenceExpression)element).resolve();
        if (resolved instanceof GrVariable && !(resolved instanceof GrParameter)) {
          ((GrVariable)resolved).setType(type);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return GroovyInspectionBundle.message("change.lvalue.type", myName, myType);
  }
}
