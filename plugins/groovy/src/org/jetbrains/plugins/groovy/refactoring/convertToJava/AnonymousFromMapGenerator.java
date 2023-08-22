// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public final class AnonymousFromMapGenerator {
  private AnonymousFromMapGenerator() {
  }

  static void writeAnonymousMap(GrListOrMap operand, GrTypeElement typeElement, final StringBuilder builder, ExpressionContext context) {
    final PsiType type = typeElement.getType();
    final PsiClass psiClass;
    final PsiSubstitutor substitutor;
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      psiClass = resolveResult.getElement();
      substitutor = resolveResult.getSubstitutor();
    }
    else {
      psiClass = null;
      substitutor = PsiSubstitutor.EMPTY;
    }
    builder.append("new ");
    TypeWriter.writeTypeForNew(builder, type, operand);
    builder.append("() {\n");

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(operand.getProject());

    final GrExpression caller = factory.createExpressionFromText("this");
    for (GrNamedArgument arg : operand.getNamedArguments()) {
      final String name = arg.getLabelName();
      final GrExpression expression = arg.getExpression();
      if (name == null || !(expression instanceof GrClosableBlock closure)) continue;

      final GrParameter[] allParameters = closure.getAllParameters();
      List<GrParameter> actual = new ArrayList<>(Arrays.asList(allParameters));
      final PsiType clReturnType = context.typeProvider.getReturnType(closure);

      GrExpression[] args = new GrExpression[allParameters.length];
      for (int i = 0; i < allParameters.length; i++) {
        args[i] = factory.createExpressionFromText(allParameters[i].getName());
      }
      boolean singleParam = allParameters.length == 1;
      for (int param = allParameters.length; param >= 0; param--) {
        if (param < allParameters.length && !(actual.get(param).isOptional() || singleParam)) continue;

        if (param < allParameters.length) {
          final GrParameter opt = actual.remove(param);
          GrExpression initializer = opt.getInitializerGroovy();
          if (initializer == null) {
            args[param] = factory.createExpressionFromText("null");
          }
          else {
            args[param] = initializer;
          }
        }

        final GrParameter[] parameters = actual.toArray(GrParameter.EMPTY_ARRAY);

        final GrSignature signature = GrClosureSignatureUtil.createSignature(parameters, clReturnType);
        final GrMethod pattern = factory.createMethodFromSignature(name, signature);

        PsiMethod found = null;
        if (psiClass != null) {
          found = psiClass.findMethodBySignature(pattern, true);
        }

        if (found != null) {
          ModifierListGenerator.writeModifiers(builder, found.getModifierList(), ModifierListGenerator.JAVA_MODIFIERS_WITHOUT_ABSTRACT);
        }
        else {
          builder.append("public ");
        }

        PsiType returnType;
        if (found != null) {
          returnType = substitutor.substitute(context.typeProvider.getReturnType(found));
        }
        else {
          returnType = signature.getReturnType();
        }

        TypeWriter.writeType(builder, returnType, operand);

        builder.append(' ').append(name);
        GenerationUtil.writeParameterList(builder, parameters, new GeneratorClassNameProvider(), context);

        final ExpressionContext extended = context.extend();
        extended.setInAnonymousContext(true);
        if (param == allParameters.length) {
          new CodeBlockGenerator(builder, extended).generateCodeBlock(allParameters, closure, false);
        }
        else {
          builder.append("{\n");
          final ExpressionGenerator expressionGenerator = new ExpressionGenerator(builder, extended);
          GenerationUtil.invokeMethodByName(caller, name, args, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
                                            expressionGenerator, arg);

          builder.append(";\n}\n");
        }
      }
    }

    builder.append("}");
  }
}
