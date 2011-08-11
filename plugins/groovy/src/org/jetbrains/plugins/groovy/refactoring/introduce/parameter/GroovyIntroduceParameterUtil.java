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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyIntroduceParameterUtil {
  private static final Logger LOG = Logger.getInstance(GroovyIntroduceParameterUtil.class);

  private GroovyIntroduceParameterUtil() {
  }

  public static PsiField[] findUsedFieldsWithGetters(GrExpression expression, PsiClass containingClass) {
    if (containingClass == null) return PsiField.EMPTY_ARRAY;
    final FieldSearcher searcher = new FieldSearcher(containingClass);
    expression.accept(searcher);
    return searcher.getResult();
  }

  public static TObjectIntHashMap<GrParameter> findParametersToRemove(GrIntroduceParameterContext context) {
    TObjectIntHashMap<GrParameter> toRemove = new TObjectIntHashMap<GrParameter>();
    if (context.var == null) {
      final GrParametersOwner parametersOwner = context.toReplaceIn;
      final GrParameter[] parameters = parametersOwner.getParameters();
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

  @Nullable
  public static PsiParameter getAnchorParameter(PsiParameterList parameterList, boolean isVarArgs) {
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (isVarArgs) {
      return length > 1 ? parameters[length - 2] : null;
    }
    else {
      return length > 0 ? parameters[length - 1] : null;
    }
  }

  public static void removeParametersFromCall(final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs, final TIntArrayList parametersToRemove) {
    parametersToRemove.forEach(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        try {
          final GrClosureSignatureUtil.ArgInfo<PsiElement> actualArg = actualArgs[paramNum];
          for (PsiElement arg : actualArg.args) {
            arg.delete();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return true;
      }
    });
  }

  public static void removeParamsFromUnresolvedCall(GrCall callExpression, PsiParameter[] parameters, TIntArrayList parametersToRemove) {
    final GrExpression[] arguments = callExpression.getExpressionArguments();
    final GrClosableBlock[] closureArguments = callExpression.getClosureArguments();
    final GrNamedArgument[] namedArguments = callExpression.getNamedArguments();

    final boolean hasNamedArgs;
    if (namedArguments.length > 0) {
      if (parameters.length > 0) {
        final PsiType type = parameters[0].getType();
        hasNamedArgs = InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
      }
      else {
        hasNamedArgs = false;
      }
    }
    else {
      hasNamedArgs = false;
    }

    parametersToRemove.forEachDescending(new TIntProcedure() {
      public boolean execute(int paramNum) {
        try {
          if (paramNum == 0 && hasNamedArgs) {
            for (GrNamedArgument namedArgument : namedArguments) {
              namedArgument.delete();
            }
          }
          else {
            if (hasNamedArgs) paramNum--;
            if (paramNum < arguments.length) {
              arguments[paramNum].delete();
            }
            else if (paramNum < arguments.length + closureArguments.length) {
              closureArguments[paramNum - arguments.length].delete();
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return true;
      }
    });
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
