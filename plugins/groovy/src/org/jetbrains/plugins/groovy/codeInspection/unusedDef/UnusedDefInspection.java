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
package org.jetbrains.plugins.groovy.codeInspection.unusedDef;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class UnusedDefInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("unused.symbol");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyUnusedSymbol";
  }

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitVariable(GrVariable variable) {
        super.visitVariable(variable);
        if (variable instanceof GrParameter) {
          PsiElement scope = ((GrParameter)variable).getDeclarationScope();
          if (scope instanceof GrMethod) {
            if (((GrMethod)scope).getBlock() == null) return;

            if (((GrMethod)scope).getHierarchicalMethodSignature().getSuperSignatures().size() > 0) {
              return;
            }
          }
        }

        if (!(variable instanceof GrField)) {
          checkVar(variable);
        }
      }

      private void checkVar(GrVariable var) {
        AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
        try {
          boolean isNotAccessedForRead = ReferencesSearch.search(var).forEach(new Processor<PsiReference>() {
            @Override
            public boolean process(PsiReference reference) {
              PsiElement element = reference.getElement();
              return !(element instanceof GrExpression && PsiUtil.isAccessedForReading((GrExpression)element));
            }
          });
          if (isNotAccessedForRead) {
            registerError(var.getNameIdentifierGroovy(), GroovyInspectionBundle.message("unused.symbol"),
                          getFixes(var),
                          ProblemHighlightType.LIKE_UNUSED_SYMBOL);
          }
        }
        finally {
          lock.finish();
        }
      }
    };
  }

  private static LocalQuickFix[] getFixes(GrVariable var) {
    if (GroovyRefactoringUtil.isLocalVariable(var)) {
      return new LocalQuickFix[]{new RemoveVarFix(var.getName())};
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private static class RemoveVarFix implements LocalQuickFix {
    private String myName;

    public RemoveVarFix(String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return GroovyInspectionBundle.message("remove.variable", myName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyInspectionBundle.message("remove.unused.variable");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (parent instanceof GrVariable) {
        Collection<PsiReference> all = ReferencesSearch.search(parent).findAll();

        for (PsiReference reference : all) {
          PsiElement e = reference.getElement();
          if (e instanceof GrReferenceExpression) {
            PsiElement p = e.getParent();
            if (p instanceof GrAssignmentExpression) {
              if (PsiUtil.isExpressionUsed(p)) {
                ((GrAssignmentExpression)p).replaceWithExpression(((GrAssignmentExpression)p).getRValue(), true);
              }
              else {
                p.delete();
              }
            }
          }
          else {
            e.delete();
          }
        }
        parent.delete();
      }
    }
  }
}
