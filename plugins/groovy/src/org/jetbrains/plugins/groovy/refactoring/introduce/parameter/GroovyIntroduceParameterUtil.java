/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyIntroduceParameterUtil {
  private GroovyIntroduceParameterUtil() {
  }

  public static PsiField[] findUsedFieldsWithGetters(GrExpression expression, PsiClass containingClass) {
    final FieldSearcher searcher = new FieldSearcher(containingClass);
    expression.accept(searcher);
    return searcher.getResult();
  }

  public static TObjectIntHashMap<GrParameter> findParametersToRemove(GrIntroduceContext context) {
    TObjectIntHashMap<GrParameter> toRemove = new TObjectIntHashMap<GrParameter>();
    if (context.var == null) {
      final GrMethod method = (GrMethod)context.scope;
      final GrParameter[] parameters = method.getParameters();
      final GrExpression expr = context.expression;
      for (int i = 0; i < parameters.length; i++) {
        GrParameter parameter = parameters[i];
        final boolean shouldRemove = ReferencesSearch.search(parameter).forEach(new Processor<PsiReference>() {
          @Override
          public boolean process(PsiReference ref) {
            final PsiElement element = ref.getElement();
            if (element == null) return false;
            return PsiTreeUtil.isAncestor(expr, element, false);
          }
        });
        if (shouldRemove) {
          toRemove.put(parameter, i);
        }
      }
    }
    return toRemove;
  }

  private static class FieldSearcher extends GroovyRecursiveElementVisitor {
    PsiClass myClass;
    private final List<PsiField> result = new ArrayList<PsiField>();

    private FieldSearcher(PsiClass aClass) {
      myClass = aClass;
    }

    public PsiField[] getResult() {
      return ContainerUtil.toArray(result, new PsiField[result.size()]);
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression ref) {
      super.visitReferenceExpression(ref);
      final GrExpression qualifier = ref.getQualifier();
      if (qualifier != null && !(qualifier instanceof GrThisReferenceExpression)) return;

      final PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiField)) return;
      final PsiMethod getter = GroovyPropertyUtils.findGetterForField((PsiField)resolved);
      if (getter != null) {
        result.add((PsiField)resolved);
      }
    }
  }
}
