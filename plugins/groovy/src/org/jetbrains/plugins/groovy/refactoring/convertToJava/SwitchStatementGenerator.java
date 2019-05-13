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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;

/**
 * @author Maxim.Medvedev
 */
public class SwitchStatementGenerator {

  private SwitchStatementGenerator() { }

  public static void generate(@NotNull StringBuilder builder,
                              @NotNull ExpressionContext context,
                              @NotNull GrSwitchStatement switchStatement) {
    final GrExpression condition = switchStatement.getCondition();
    final GrCaseSection[] caseSections = switchStatement.getCaseSections();

    final PsiType type = condition == null ? null : TypesUtil.unboxPrimitiveTypeWrapper(condition.getType());
    if (type == null || isValidTypeForSwitchSelector(type)) {
      generateSwitch(builder, context, condition, caseSections);
    }
    else {
      generateIfs(builder, context, condition, caseSections);
    }
  }

  private static boolean isValidTypeForSwitchSelector(@NotNull PsiType type) {
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) {
      return true;
    }

    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && aClass.isEnum()) {
      return true;
    }

    return false;
  }

  private static void generateIfs(@NotNull StringBuilder builder,
                                  @NotNull ExpressionContext context,
                                  @NotNull GrExpression condition,
                                  @NotNull GrCaseSection[] caseSections) {
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

  private static void generateIfFromCaseSection(@NotNull StringBuilder builder,
                                                @NotNull ExpressionContext context,
                                                @NotNull final GrCaseSection[] caseSections,
                                                final int i,
                                                @NotNull final GrExpression[] args) {

    GenerationUtil.writeStatement(builder, context, null, new StatementWriter() {
      @Override
      public void writeStatement(StringBuilder builder, ExpressionContext context) {
        if (caseSections.length == 1) {
          final GrCaseLabel[] labels = caseSections[0].getCaseLabels();
          if (labels.length == 1 && labels[0].isDefault()) {
            builder.append("if(true)");
          }
        }

        GrCaseSection section = caseSections[i];
        final GrCaseLabel[] labels = section.getCaseLabels();
        final boolean isCase = labels.length > 1 || !labels[0].isDefault();

        if (isCase) {
          writeCondition(builder, context, section, labels, args);
        }
        writeCaseBody(builder, context, i, caseSections);

        if (isCase && i != caseSections.length - 1) {
          builder.append("\nelse ");
          StringBuilder elseBuilder = new StringBuilder();
          final ExpressionContext elseContext = context.extend();

          generateIfFromCaseSection(elseBuilder, elseContext, caseSections, i + 1, args);
          GenerationUtil.insertStatementFromContextBefore(builder, elseContext);
          builder.append(elseBuilder);
        }
        if (!context.myStatements.isEmpty()) {
          context.setInsertCurlyBrackets();
        }
      }
    });
  }

  private static void writeCaseBody(@NotNull StringBuilder builder,
                                    @NotNull ExpressionContext context,
                                    int i,
                                    @NotNull GrCaseSection[] caseSections) {
    builder.append("{\n");

    final ExpressionContext extended = context.extend();
    CodeBlockGenerator generator = new CodeBlockGenerator(builder, extended);

    Outer:
    for (int j = i; j < caseSections.length; j++) {
      GrCaseSection curSection = caseSections[j];
      final GrStatement[] statements = curSection.getStatements();
      for (GrStatement statement : statements) {
        if (statement instanceof GrBreakStatement && ((GrBreakStatement)statement).getLabelIdentifier() == null) {
          break Outer;
        }
        statement.accept(generator);
        builder.append("\n");
      }
      if (brakesFlow(curSection)) break;
    }

    builder.append('}');
  }

  private static boolean brakesFlow(GrCaseSection section) {
    final GrStatement[] statements = section.getStatements();
    return statements.length > 0 && !ControlFlowUtils.statementMayCompleteNormally(ArrayUtil.getLastElement(statements));
  }

  private static void writeCondition(StringBuilder builder,
                                     ExpressionContext context,
                                     GrCaseSection section,
                                     GrCaseLabel[] labels,
                                     GrExpression[] args) {
    builder.append("if (");
    for (GrCaseLabel label : labels) {
      if (label.isDefault()) {
        builder.append("true");
      }
      else {
        GenerationUtil.invokeMethodByName(
          label.getValue(),
          "isCase",
          args,
          GrNamedArgument.EMPTY_ARRAY,
          GrClosableBlock.EMPTY_ARRAY,
          new ExpressionGenerator(builder, context),
          section
        );
      }
      builder.append("||");
    }
    builder.delete(builder.length() - 2, builder.length());
    builder.append(") ");
  }

  private static String generateConditionVar(@NotNull StringBuilder builder,
                                             @NotNull ExpressionContext context,
                                             @NotNull GrExpression condition) {
    StringBuilder conditionBuilder = new StringBuilder();
    final PsiType type = condition.getType();
    final String varName = GenerationUtil.validateName("switchArg", condition, context);
    conditionBuilder.append("final ");
    TypeWriter.writeType(conditionBuilder, type, condition);
    conditionBuilder.append(' ').append(varName).append(" = ");
    condition.accept(new ExpressionGenerator(conditionBuilder, context));
    conditionBuilder.append(";\n");
    GenerationUtil.insertStatementFromContextBefore(builder, context);
    builder.append(conditionBuilder);
    return varName;
  }

  private static void generateSwitch(@NotNull StringBuilder builder,
                                     @NotNull ExpressionContext context,
                                     @Nullable GrExpression condition,
                                     @NotNull GrCaseSection[] caseSections) {
    builder.append("switch (");
    if (condition != null) {
      condition.accept(new ExpressionGenerator(builder, context));
    }
    builder.append(") {\n");

    final ExpressionContext innerContext = context.extend();
    for (GrCaseSection section : caseSections) {
      generateCaseSection(builder, context, innerContext, section);
    }

    builder.append('}');
  }

  private static void generateCaseSection(@NotNull StringBuilder builder,
                                          @NotNull ExpressionContext context,
                                          @NotNull ExpressionContext innerContext,
                                          @NotNull GrCaseSection section) {
    for (GrCaseLabel label : section.getCaseLabels()) {
      writeLabel(builder, context, label);
    }

    final GrStatement[] statements = section.getStatements();
    CodeBlockGenerator generator = new CodeBlockGenerator(builder, innerContext);
    for (GrStatement statement : statements) {
      statement.accept(generator);
      builder.append("\n");
    }
  }

  private static void writeLabel(@NotNull StringBuilder builder,
                                 @NotNull ExpressionContext context,
                                 @NotNull GrCaseLabel label) {
    if (label.isDefault()) {
      builder.append("default");
    }
    else {
      builder.append("case ");
      final GrExpression value = label.getValue();
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
  }
}
