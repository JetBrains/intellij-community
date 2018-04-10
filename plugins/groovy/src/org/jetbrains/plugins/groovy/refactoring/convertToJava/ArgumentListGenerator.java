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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
class ArgumentListGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ArgumentListGenerator");

  private final StringBuilder myBuilder;
  private final ExpressionGenerator myExpressionGenerator;


  public ArgumentListGenerator(StringBuilder builder, ExpressionContext context) {
    myBuilder = builder;
    myExpressionGenerator = new ExpressionGenerator(builder, context);
  }

  public void generate(@Nullable GrClosureSignature signature,
                       @NotNull GrExpression[] exprs,
                       @NotNull GrNamedArgument[] namedArgs,
                       @NotNull GrClosableBlock[] clArgs,
                       @NotNull GroovyPsiElement context) {
    GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos =
      signature == null ? null : GrClosureSignatureUtil.mapParametersToArguments(signature, namedArgs, exprs, clArgs, context, false, false);

    if (argInfos == null && signature != null) {
      argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, namedArgs, exprs, clArgs, context, true, true);
    }

    final PsiSubstitutor substitutor = signature == null ? PsiSubstitutor.EMPTY : signature.getSubstitutor();
    if (argInfos == null || ArrayUtil.contains(null, argInfos)) {
      generateSimple(exprs, namedArgs, clArgs, context, substitutor);
      return;
    }

    final GrClosureParameter[] params = signature.getParameters();

    final Project project = context.getProject();
    myBuilder.append('(');
    boolean hasCommaAtEnd = false;
    for (int i = 0; i < argInfos.length; i++) {
      GrClosureSignatureUtil.ArgInfo<PsiElement> arg = argInfos[i];
      if (arg == null) continue;
      final GrClosureParameter param = params[i];
      boolean generated = arg.isMultiArg ? generateMultiArg(arg, param, substitutor, project, context) : generateSingeArg(arg, param);
      if (generated) {
        hasCommaAtEnd = true;
        myBuilder.append(", ");
      }
    }

    if (hasCommaAtEnd) {
      myBuilder.delete(myBuilder.length() - 2, myBuilder.length());
      //myBuilder.removeFromTheEnd(2);
    }

    myBuilder.append(')');
  }

  private boolean generateSingeArg(GrClosureSignatureUtil.ArgInfo<PsiElement> arg, GrClosureParameter param) {
    boolean argExists = !arg.args.isEmpty() && arg.args.get(0) != null;
    if (argExists) {
      final PsiElement actual = arg.args.get(0);
      LOG.assertTrue(actual instanceof GrExpression);
      final PsiType type = param.getType();
      final PsiType declaredType = GenerationUtil.getDeclaredType((GrExpression)actual, myExpressionGenerator.getContext());
      if (type != null && declaredType != null && !TypesUtil.isAssignableByMethodCallConversion(type, declaredType,(GrExpression)actual
      )) {
        myBuilder.append('(');
        TypeWriter.writeType(myBuilder, type, actual);
        myBuilder.append(')');
      }
      ((GrExpression)actual).accept(myExpressionGenerator);
      return true;
    }
    else {
      /*final GrExpression initializer = param.getInitializerGroovy();
      if (initializer != null) {
        initializer.accept(myExpressionGenerator);
      }
      else {
        myBuilder.append("???"); //todo add something more consistent
      }*/
      return false;
    }
  }

  private boolean generateMultiArg(GrClosureSignatureUtil.ArgInfo<PsiElement> arg,
                                   GrClosureParameter param,
                                   PsiSubstitutor substitutor,
                                   Project project,
                                   GroovyPsiElement context) {
    final PsiType type = param.getType();
    //todo find out if param is array in case of it has declared type

    if (type instanceof PsiEllipsisType) {
      for (PsiElement element : arg.args) {
        LOG.assertTrue(element instanceof GrExpression);
        ((GrExpression)element).accept(myExpressionGenerator);
        myBuilder.append(", ");
      }
      if (!arg.args.isEmpty()) {
        myBuilder.delete(myBuilder.length() - 2, myBuilder.length());
        return true;
      }
      else {
        return false;
      }
    }
    else if (type instanceof PsiArrayType) {
      myBuilder.append("new ");
      if (arg.args.isEmpty()) {
        TypeWriter.writeType(myBuilder, ((PsiArrayType)type).getComponentType(), context);
        myBuilder.append("[0]");
      }
      else {
        TypeWriter.writeTypeForNew(myBuilder, type, context);
        myBuilder.append("{");

        for (PsiElement element : arg.args) {
          LOG.assertTrue(element instanceof GrExpression);
          ((GrExpression)element).accept(myExpressionGenerator);
          myBuilder.append(", ");
        }
        if (!arg.args.isEmpty()) myBuilder.delete(myBuilder.length() - 2, myBuilder.length());
        //if (arg.args.size() > 0) myBuilder.removeFromTheEnd(2);
        myBuilder.append('}');
      }
    }
    else {
      final GrExpression listOrMap = GroovyRefactoringUtil.generateArgFromMultiArg(substitutor, arg.args, type, project);
      LOG.assertTrue(listOrMap instanceof GrListOrMap);
      listOrMap.accept(myExpressionGenerator);
    }
    return true;
  }

  private void generateSimple(GrExpression[] exprs,
                              GrNamedArgument[] namedArgs,
                              GrClosableBlock[] closures,
                              GroovyPsiElement context,
                              PsiSubstitutor substitutor) {
    myBuilder.append('(');
    if (namedArgs.length > 0) {
      final GrExpression listOrMap =
        GroovyRefactoringUtil.generateArgFromMultiArg(substitutor, Arrays.asList(namedArgs), null, context.getProject());
      LOG.assertTrue(listOrMap instanceof GrListOrMap);
      listOrMap.accept(myExpressionGenerator);
      myBuilder.append(", ");
    }

    for (GrExpression expr : exprs) {
      expr.accept(myExpressionGenerator);
      myBuilder.append(", ");
    }

    for (GrClosableBlock closure : closures) {
      closure.accept(myExpressionGenerator);
      myBuilder.append(", ");
    }

    if (namedArgs.length + exprs.length + closures.length > 0) {
      myBuilder.delete(myBuilder.length()-2, myBuilder.length());
      //myBuilder.removeFromTheEnd(2);
    }

    myBuilder.append(')');
  }
}
