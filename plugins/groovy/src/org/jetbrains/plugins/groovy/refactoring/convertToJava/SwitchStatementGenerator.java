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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;

/**
 * @author Maxim.Medvedev
 */
public class SwitchStatementGenerator {

  private static final boolean LANGUAGE_LEVEL_7_OR_HIGHER = false;

  private SwitchStatementGenerator() {
  }

  public static void generate(StringBuilder builder, ExpressionContext context, GrSwitchStatement switchStatement) {
    final GrExpression condition = switchStatement.getCondition();
    final GrCaseSection[] caseSections = switchStatement.getCaseSections();

    final PsiType type = condition == null ? null : TypesUtil.unboxPrimitiveTypeWrapper(condition.getType());
    if (type == null || HighlightUtil.isValidTypeForSwitchSelector(type, LANGUAGE_LEVEL_7_OR_HIGHER)) {
      generateSwitch(builder, context, condition, caseSections);
    }
    else {
      generateIfs(builder, context, condition, caseSections);
    }
  }

  private static void generateIfs(StringBuilder builder, ExpressionContext context, GrExpression condition, GrCaseSection[] caseSections) {
    if (caseSections.length == 1 && caseSections[0].getCaseLabel().isDefault()) {
      //todo
    }

    final GrExpression ref;
    if (condition instanceof GrReferenceExpression) {
      ref = condition;
    }
    else {
      final String varName = generateConditionVar(builder, context, condition);
      ref = GroovyPsiElementFactory.getInstance(context.project).createExpressionFromText(varName);
    }
    final GrExpression[] args = {ref};
    generateIfFromCaseSection(builder, context, caseSections, 0, args);
  }

  private static void generateIfFromCaseSection(StringBuilder builder,
                                                ExpressionContext context,
                                                final GrCaseSection[] caseSections,
                                                final int i,
                                                final GrExpression[] args) {

    GenerationUtil.writeStatement(builder, context, null, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        GrCaseSection section = caseSections[i];
        boolean isCase = !section.getCaseLabel().isDefault();

        if (isCase) {
          builder.append("if (");
          GenerationUtil.invokeMethodByName(
            section.getCaseLabel().getValue(),
            "isCase",
            args,
            GrNamedArgument.EMPTY_ARRAY,
            GrClosableBlock.EMPTY_ARRAY,
            new ExpressionGenerator(builder, context),
            section
          );
          builder.append(") {\n");
        }
        final ExpressionContext extended = context.extend();
        CodeBlockGenerator generator = new CodeBlockGenerator(builder, extended);

        Outer:
        for (int j = i; j < caseSections.length; j++) {
          section = caseSections[j];
          final GrStatement[] statements = section.getStatements();
          for (GrStatement statement : statements) {
            if (statement instanceof GrBreakStatement && ((GrBreakStatement)statement).getLabelIdentifier() == null) {
              break Outer;
            }
            statement.accept(generator);
            builder.append("\n");
          }
        }

        if (isCase && i + 1 < caseSections.length) {
          builder.append("}\nelse ");
          StringBuilder elseBuilder = new StringBuilder();
          final ExpressionContext elseContext = context.extend();

          generateIfFromCaseSection(elseBuilder, elseContext, caseSections, i + 1, args);
          GenerationUtil.insertStatementFromContextBefore(builder, elseContext);
          builder.append(elseBuilder);
        }
        if (context.myStatements.size() > 0) context.setInsertCurlyBrackets();
      }
    });
  }

  private static String generateConditionVar(StringBuilder builder,
                                             ExpressionContext context,
                                             GrExpression condition) {
    StringBuilder conditionBuilder = new StringBuilder();
    final PsiType type = condition.getType();
    final String varName = GenerationUtil.validateName("switchArg", condition, context);
    conditionBuilder.append("final ");
    GenerationUtil.writeType(conditionBuilder, type, condition);
    conditionBuilder.append(" ").append(varName).append(" = ");
    condition.accept(new ExpressionGenerator(conditionBuilder, context));
    conditionBuilder.append(";\n");
    GenerationUtil.insertStatementFromContextBefore(builder, context);
    builder.append(conditionBuilder);
    return varName;
  }

  private static void generateSwitch(StringBuilder builder,
                                     ExpressionContext context,
                                     GrExpression condition,
                                     GrCaseSection[] caseSections) {
    builder.append("switch (");
    if (condition != null) {
      condition.accept(new ExpressionGenerator(builder, context));
    }
    builder.append(") {\n");

    final ExpressionContext innerContext = context.extend();
    for (GrCaseSection section : caseSections) {
      generateCaseSection(builder, context, innerContext, section);
    }

    builder.append("}");
  }

  private static void generateCaseSection(StringBuilder builder,
                                          ExpressionContext context,
                                          ExpressionContext innerContext,
                                          GrCaseSection section) {
    if (section.getCaseLabel().isDefault()) {
      builder.append("default");
    }
    else {
      builder.append("case ");
      final GrExpression value = section.getCaseLabel().getValue();
      Object evaluated;
      try {
        evaluated = GroovyConstantExpressionEvaluator.evaluate(value);
      }
      catch (Throwable e) {
        evaluated = null;
      }

      if (evaluated != null) {
        builder.append(evaluated);
      }
      else if (value != null) {
        value.accept(new ExpressionGenerator(builder, context));
      }
    }

    builder.append(":\n");
    final GrStatement[] statements = section.getStatements();
    CodeBlockGenerator generator = new CodeBlockGenerator(builder, innerContext);
    for (GrStatement statement : statements) {
      statement.accept(generator);
      builder.append("\n");
    }
  }
}
