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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.conversions.ConvertGStringToStringIntention;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.*;

/**
 * @author Maxim.Medvedev
 */
public class ExpressionGenerator extends Generator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ExpressionGenerator");

  private StringBuilder builder;
  private GroovyPsiElementFactory factory;

  private final ExpressionContext context;

  public ExpressionGenerator(StringBuilder builder, ExpressionContext context) {
    this.builder = builder;
    this.context = context;

    factory = GroovyPsiElementFactory.getInstance(context.project);
  }

  public ExpressionGenerator(Project project) {
    this(new StringBuilder(), new ExpressionContext(project));
  }

  @Override
  public StringBuilder getBuilder() {
    return builder;
  }

  @Override
  public ExpressionContext getContext() {
    return context;
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    new ClosureGenerator(builder, context).generate(closure);
  }


  public void visitExpression(GrExpression expression) {
    LOG.error("this method should not be invoked");
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    generateMethodCall(methodCallExpression);
  }

  private void generateMethodCall(GrMethodCall methodCallExpression) {
    final GrExpression invoked = methodCallExpression.getInvokedExpression();
    final GrExpression[] exprs = methodCallExpression.getExpressionArguments();
    final GrNamedArgument[] namedArgs = methodCallExpression.getNamedArguments();
    final GrClosableBlock[] clArgs = methodCallExpression.getClosureArguments();
    if (invoked instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)invoked).advancedResolve();
      if (resolveResult.getElement() instanceof PsiMethod) {
        final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();//todo replace null-qualifier with this-reference

        invokeMethodOn(
          ((PsiMethod)resolveResult.getElement()),
          qualifier,
          exprs, namedArgs, clArgs,
          resolveResult.getSubstitutor(),
          methodCallExpression
        );
        return;
      }
    }

    invokeMethodByName(invoked, "call", exprs, namedArgs, clArgs, this, methodCallExpression);
  }

  @Override
  public void visitNewExpression(GrNewExpression newExpression) {
    boolean hasFieldInitialization = hasFieldInitialization(newExpression);
    StringBuilder builder;

    final PsiType type = newExpression.getType();
    final String varName;
    if (hasFieldInitialization) {
      builder = new StringBuilder();
      varName = suggestVarName(type, newExpression, this.context);
      writeType(builder, type);
      builder.append(" ").append(varName).append(" = ");
    }
    else {
      varName = null;
      builder = this.builder;
    }

    final GrExpression qualifier = newExpression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      builder.append(".");
    }

    final GrTypeElement typeElement = newExpression.getTypeElement();
    final GrArrayDeclaration arrayDeclaration = newExpression.getArrayDeclaration();
    final GrCodeReferenceElement referenceElement = newExpression.getReferenceElement();

    builder.append("new ");
    if (typeElement != null) {
      final PsiType builtIn = typeElement.getType();
      LOG.assertTrue(builtIn instanceof PsiPrimitiveType);
      final PsiType boxed = TypesUtil.boxPrimitiveType(builtIn, newExpression.getManager(), newExpression.getResolveScope());
      writeType(builder, boxed);
    }
    else if (referenceElement != null) {
      writeCodeReferenceElement(builder, referenceElement);
    }

    final GrArgumentList argList = newExpression.getArgumentList();
    if (argList!=null) {

      GrClosureSignature signature = null;

      final GroovyResolveResult resolveResult = newExpression.resolveConstructorGenerics();
      final PsiElement constructor = resolveResult.getElement();
      if (constructor instanceof PsiMethod) {
        signature = GrClosureSignatureUtil.createSignature((PsiMethod)constructor, resolveResult.getSubstitutor());
      }
      else if (referenceElement != null) {
        final GroovyResolveResult clazzResult = referenceElement.advancedResolve();
        final PsiElement clazz = clazzResult.getElement();
        if (clazz instanceof PsiClass && ((PsiClass)clazz).getConstructors().length == 0) {
          signature = GrClosureSignatureUtil.createSignature(PsiParameter.EMPTY_ARRAY, null);
        }
      }

      final GrNamedArgument[] namedArgs = hasFieldInitialization ? GrNamedArgument.EMPTY_ARRAY : argList.getNamedArguments();
      new ArgumentListGenerator(builder, context).generate(
        signature,
        argList.getExpressionArguments(),
        namedArgs,
        GrClosableBlock.EMPTY_ARRAY,
        newExpression
      );
    }

    final GrAnonymousClassDefinition anonymous = newExpression.getAnonymousClassDefinition();
    if (anonymous != null) {
      writeTypeBody(builder, anonymous);
    }

    if (arrayDeclaration != null) {
      final GrExpression[] boundExpressions = arrayDeclaration.getBoundExpressions();
      for (GrExpression boundExpression : boundExpressions) {
        builder.append("[");
        boundExpression.accept(this);
        builder.append("]");
      }
      if (boundExpressions.length == 0) {
        builder.append("[]");
      }
    }

    if (hasFieldInitialization) {
      context.myStatements.add(builder.toString());
      final GrNamedArgument[] namedArguments = argList.getNamedArguments();
      for (GrNamedArgument namedArgument : namedArguments) {
        final String fieldName = namedArgument.getLabelName();
        if (fieldName == null) {
          //todo try to initialize field
          final GrArgumentLabel label = namedArgument.getLabel();
          LOG.info("cannot initialize field " + (label == null ? "<null>" : label.getText()));
        }
        else {
          final GroovyResolveResult resolveResult = referenceElement.advancedResolve();
          final PsiElement resolved = resolveResult.getElement();
          LOG.assertTrue(resolved instanceof PsiClass);
          initializeField(varName, type,  ((PsiClass)resolved), resolveResult.getSubstitutor(), fieldName, namedArgument.getExpression());
        }
      }
    }
  }

  private void initializeField(String varName,
                               PsiType type,
                               PsiClass resolved,
                               PsiSubstitutor substitutor,
                               String fieldName,
                               GrExpression expression) {
    StringBuilder builder = new StringBuilder();
    final PsiMethod setter = GroovyPropertyUtils.findPropertySetter(resolved, fieldName, false, true);
    if (setter != null) {
      final GrVariableDeclaration var = factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, null, type, varName);
      final GrReferenceExpression caller = factory.createReferenceExpressionFromText(varName, var);
      invokeMethodOn(setter, caller, new GrExpression[]{expression}, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY, substitutor,
                     expression);
    }
    else {
      builder.append(varName).append(".").append(fieldName).append(" = ");
      expression.accept(new ExpressionGenerator(builder, context));
    }
    context.myStatements.add(builder.toString());
  }

  @Override
  public String toString() {
    return builder.toString();
  }

  private static boolean hasFieldInitialization(GrNewExpression newExpression) {
    final GrArgumentList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return false;
    if (argumentList.getNamedArguments().length == 0) return false;

    final GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return false;
    final GroovyResolveResult resolveResult = newExpression.resolveConstructorGenerics();
    final PsiElement constructor = resolveResult.getElement();
    if (constructor instanceof PsiMethod) {
      return ((PsiMethod)constructor).getParameterList().getParametersCount() == 0;
    }

    final PsiElement resolved = refElement.resolve();
    return resolved instanceof PsiClass;
  }

  private void writeTypeBody(StringBuilder builder, GrAnonymousClassDefinition anonymous) {
    //todo write type body for anonymous class
    throw new UnsupportedOperationException();
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    generateMethodCall(applicationStatement);
  }

  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
    final GrExpression condition = expression.getCondition();
    final GrExpression thenBranch = expression.getThenBranch();
    final GrExpression elseBranch = expression.getElseBranch();

    final PsiType type = condition.getType();
    if (type == null || TypesUtil.unboxPrimitiveTypeWrapper(type) == PsiType.BOOLEAN) {
      condition.accept(this);
    }
    else {
      GenerationUtil.invokeMethodByName(
        condition,
        "asBoolean",
        GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
        this,
        expression
      );
    }

    builder.append("?");
    thenBranch.accept(this);
    builder.append(":");
    elseBranch.accept(this);
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    final GrExpression lValue = expression.getLValue();
    GrExpression rValue = expression.getRValue();
    final IElementType token = expression.getOperationToken();


    if (token == mASSIGN) {
      lValue.accept(this);
      builder.append(" = ");
      if (rValue != null) {
        rValue.accept(this);
      }
      return;
    }

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PsiMethod) {
      lValue.accept(this);
      builder.append(" = ");
      if (rValue == null) {
        rValue = factory.createExpressionFromText("null");
      }
      invokeMethodOn(
        ((PsiMethod)resolved),
        lValue,
        new GrExpression[]{rValue},
        GrNamedArgument.EMPTY_ARRAY,
        GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        expression
      );
    }
    else {
      writeSimpleBinaryExpression(expression.getOpToken(), lValue, rValue);
    }
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    final GrExpression left = expression.getLeftOperand();
    GrExpression right = expression.getRightOperand();
    final PsiType ltype = left.getType();
    final PsiElement token = expression.getOperationToken();
    final IElementType op = expression.getOperationTokenType();

    if (op == mREGEX_FIND) {
      builder.append(GroovyCommonClassNames.JAVA_UTIL_REGEX_PATTERN).append(".compile(");
      if (right != null) {
        right.accept(this);
      }
      builder.append(").matcher(");
      left.accept(this);
      builder.append(")");
      return;
    }
    if (op == mREGEX_MATCH) {
      builder.append(GroovyCommonClassNames.JAVA_UTIL_REGEX_PATTERN).append(".matches(");
      if (right != null) {
        right.accept(this);
      }
      builder.append(", ");
      left.accept(this);
      builder.append(')');
      return;
    }
    if (GenerationSettings.dontReplaceOperatorsWithMethodsForNumbers &&
        (TypesUtil.isNumericType(ltype) && (right == null || TypesUtil.isNumericType(right.getType())) ||
         op == mPLUS && ltype != null && TypesUtil.typeEqualsToText(ltype, CommonClassNames.JAVA_LANG_STRING))) {
      writeSimpleBinaryExpression(token, left, right);
      return;
    }

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      if (right == null) {
        right = factory.createExpressionFromText("null");
      }
      invokeMethodOn(
        ((PsiMethod)resolved),
        left,
        new GrExpression[]{right},
        GrNamedArgument.EMPTY_ARRAY,
        GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        expression
      );
      if (op == mGE) builder.append(" >= 0");
      else if (op == mGT) builder.append(" > 0");
      else if (op == mLT) builder.append(" < 0");
      else if (op == mLE) builder.append(" <= 0");
    }
    else {
      writeSimpleBinaryExpression(token, left, right);
    }
  }

  private void writeSimpleBinaryExpression(PsiElement opToken, GrExpression left, GrExpression right) {
    left.accept(this);
    builder.append(" ");
    builder.append(opToken.getText());
    if (right != null) {
      builder.append(" ");
      right.accept(this);
    }
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    final boolean postfix = expression instanceof GrPostfixExpression;

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();
    final GrExpression operand = expression.getOperand();

    IElementType opType = expression.getOperationTokenType();

    if (resolved instanceof PsiMethod) {
      if (opType == mLNOT) {
        builder.append('!');
      }
      else if (!postfix && (opType == mINC || opType == mDEC)) {
        if (generatePrefixIncDec((PsiMethod)resolved, operand, expression)) return;
      }

      invokeMethodOn(
        ((PsiMethod)resolved),
        operand,
        GrExpression.EMPTY_ARRAY,
        GrNamedArgument.EMPTY_ARRAY,
        GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        expression
      );
    }
    else if (operand != null) {
      if (postfix) {
        operand.accept(this);
        builder.append(expression.getOperationToken().getText());
      }
      else {
        builder.append(expression.getOperationToken().getText());
        operand.accept(this);
      }
    }
  }

  private boolean generatePrefixIncDec(PsiMethod method,
                                       GrExpression operand,
                                       GrUnaryExpression unary) {
    if (!(operand instanceof GrReferenceExpression)) return false;

    final GrExpression qualifier = ((GrReferenceExpression)operand).getQualifier();
    final GroovyResolveResult resolveResult = ((GrReferenceExpression)operand).advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)resolved)) {
      final PsiMethod getter = (PsiMethod)resolved;
      final String propertyName = GroovyPropertyUtils.getPropertyNameByGetter(getter);
      final PsiType type;
      if (qualifier == null) {
        type = null;
      }
      else {
        type = qualifier.getType();
        if (type == null) return false;
      }
      final PsiMethod setter = GroovyPropertyUtils.findPropertySetter(type, propertyName, unary);
      if (setter == null) return false;

      final ExpressionGenerator generator = new ExpressionGenerator(new StringBuilder(), context);
      generator.invokeMethodOn(
        method,
        operand,
        GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        unary
      );

      final GrExpression fromText = factory.createExpressionFromText(generator.toString(), unary);
      invokeMethodOn(
        setter,
        qualifier,
        new GrExpression[]{fromText}, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        unary
      );
    }
    else if (resolved instanceof PsiVariable) {
      builder.append('(');
      operand.accept(this);
      builder.append(" = ");
      invokeMethodOn(
        method,
        operand,
        GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        unary
      );
      builder.append(')');
    }
    return true;
  }

  @Override
  //doesn't visit GrString and regexps
  public void visitLiteralExpression(GrLiteral literal) {
    final TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(literal);

    final String text = literal.getText();
    final Object value;
    if (text.startsWith("'''") || text.startsWith("\"\"\"")) {
      String string = ((String)literal.getValue());
      LOG.assertTrue(string != null);
      string = string.replace("\n", "\\n").replace("\r", "\\r");
      value = string;
    }
    else {
      value = literal.getValue();
    }

    boolean isChar = false;
    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof SubtypeConstraint && TypesUtil.unboxPrimitiveTypeWrapper(constraint.getDefaultType()) == PsiType.CHAR) {
        isChar = true;
      }
    }
    if (isChar) {
      builder.append('\'').append(StringUtil.escapeQuotes(String.valueOf(value))).append('\'');
    }
    else if (value instanceof String) {
      builder.append('"').append(StringUtil.escapeQuotes((String)value)).append('"');
    }
    else {
      builder.append(value);
    }

    //todo replace with character in some cases
  }

  @Override
  public void visitGStringExpression(GrString gstring) {
    final String newExprText = ConvertGStringToStringIntention.convertGStringLiteralToStringLiteral(gstring);
    final GrExpression newExpr = factory.createExpressionFromText(newExprText);
    newExpr.accept(this);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    final GrExpression qualifier = referenceExpression.getQualifier();
    final GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    final IElementType type = referenceExpression.getDotTokenType();

    GrExpression qualifierToUse = qualifier;

    if (type == mMEMBER_POINTER) {
      LOG.assertTrue(qualifier != null);
      builder.append("new ").append(GroovyCommonClassNames.ORG_CODEHAUS_GROOVY_RUNTIME_METHOD_CLOSURE).append("(");
      qualifier.accept(this);
      builder.append(", \"").append(referenceExpression.getReferenceName()).append("\")");
      return;
    }

    if (type == mOPTIONAL_DOT) {
      LOG.assertTrue(qualifier != null);

      String qualifierName = createVarByInitializer(qualifier);
      builder.append(qualifierName).append(" == null ? null : ");

      qualifierToUse = factory.createReferenceExpressionFromText(qualifierName, referenceExpression);
    }


    if (resolveResult.isInvokedOnProperty()) {
      LOG.assertTrue(resolved instanceof PsiMethod);
      invokeMethodOn(
        ((PsiMethod)resolved),
        qualifierToUse,
        GrExpression.EMPTY_ARRAY,
        GrNamedArgument.EMPTY_ARRAY,
        GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        referenceExpression
      );
    }
    else {
      if (qualifierToUse != null) {
        qualifierToUse.accept(this);
        builder.append('.');
      }
      if (resolved instanceof PsiNamedElement) {
        final String refName = ((PsiNamedElement)resolved).getName();
        builder.append(refName);
      }
      else {
        final String refName = referenceExpression.getReferenceName();
        if (refName != null) {
          builder.append(refName);
        }
        else {
          final PsiElement nameElement = referenceExpression.getReferenceNameElement();
          if (nameElement instanceof GrExpression) {
            ((GrExpression)nameElement).accept(this);
          }
          else if (nameElement != null) {
            builder.append(nameElement.toString());
          }
        }
      }
    }
  }

  private String createVarByInitializer(GrExpression initializer) {
    if (initializer instanceof GrReferenceExpression) {
      final GrExpression qualifier = ((GrReferenceExpression)initializer).getQualifier();
      if (qualifier == null) {
        final PsiElement resolved = ((GrReferenceExpression)initializer).resolve();
        if (resolved instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable((GrVariable)resolved)) {

          //don't create new var. it is already exists
          return ((GrVariable)resolved).getName();
        }
      }
    }
    final String name = suggestVarName(initializer, context);
    final StringBuilder builder = new StringBuilder();
    builder.append("final ");
    writeType(builder, initializer.getType());
    builder.append(' ').append(name).append(" = ");
    initializer.accept(new ExpressionGenerator(builder, context));
    builder.append(';');
    context.myStatements.add(builder.toString());
    return name;
  }

  @Override
  public void visitThisSuperReferenceExpression(GrThisSuperReferenceExpression expr) {
    //doesn't visit constructor invocation
    LOG.assertTrue(!(expr.getParent() instanceof GrConstructorInvocation));

    final PsiElement resolved = expr.resolve();
    LOG.assertTrue(resolved instanceof PsiClass);

    builder.append(((PsiClass)resolved).getQualifiedName()).append(".").append(expr.getReferenceName());
  }

  @Override
  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    final GrTypeElement typeElement = typeCastExpression.getCastTypeElement();
    final GrExpression operand = typeCastExpression.getOperand();
    generateCast(typeElement, operand);
  }

  private void generateCast(GrTypeElement typeElement, GrExpression operand) {
    builder.append("(");
    typeElement.accept(this);
    builder.append(")");
    if (operand != null) {
      operand.accept(this);
    }
  }

  @Override
  public void visitSafeCastExpression(GrSafeCastExpression typeCastExpression) {
    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(typeCastExpression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();

    final GrTypeElement typeElement = typeCastExpression.getCastTypeElement();
    if (resolved instanceof PsiMethod) {
      final GrExpression typeParam = factory.createExpressionFromText(typeElement.getText());
      invokeMethodOn(
        ((PsiMethod)resolved),
        typeCastExpression.getOperand(),
        new GrExpression[]{typeParam},
        GrNamedArgument.EMPTY_ARRAY,
        GrClosableBlock.EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        typeCastExpression
      );
    }
    else {
      generateCast(typeElement, typeCastExpression.getOperand());
    }
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    final GrExpression operand = expression.getOperand();
    final GrTypeElement typeElement = expression.getTypeElement();
    operand.accept(this);
    builder.append(" instanceof ");
    typeElement.accept(this);
  }

  @Override
  public void visitArrayTypeElement(GrArrayTypeElement typeElement) {
    //todo
    throw new UnsupportedOperationException();
  }

  @Override
  public void visitBuiltinTypeElement(GrBuiltInTypeElement typeElement) {
    //todo
    throw new UnsupportedOperationException();
  }

  @Override
  public void visitClassTypeElement(GrClassTypeElement typeElement) {
    //todo
    throw new UnsupportedOperationException();
  }

  @Override
  public void visitBuiltinTypeClassExpression(GrBuiltinTypeClassExpression expression) {
    final IElementType type = expression.getFirstChild().getNode().getElementType();
    final String boxed = TypesUtil.getPsiTypeName(type);
    builder.append(boxed).append(".class");
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    builder.append("(");
    final GrExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
    builder.append(")");
  }

  @Override
  public void visitIndexProperty(GrIndexProperty expression) {
    final GrExpression selectedExpression = expression.getSelectedExpression();
    final PsiType thisType = selectedExpression.getType();

    final GrArgumentList argList = expression.getArgumentList();
    final PsiType[] argTypes = PsiUtil.getArgumentTypes(argList, false, null);
    final PsiManager manager = expression.getManager();
    final GlobalSearchScope resolveScope = expression.getResolveScope();

    final GrExpression[] exprArgs = argList.getExpressionArguments();
    final GrNamedArgument[] namedArgs = argList.getNamedArguments();

    if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, manager, resolveScope)) {
      expression.accept(this);
      builder.append("[");
      final GrExpression arg = exprArgs[0];
      arg.accept(this);
      builder.append("]");
      return;
    }

    final GroovyResolveResult candidate = PsiImplUtil.getIndexPropertyMethodCandidate(thisType, argTypes, expression);
    invokeMethodByResolveResult(
      selectedExpression, candidate, "getAt", exprArgs, namedArgs, GrClosableBlock.EMPTY_ARRAY, this, expression
    );
  }

  public void invokeMethodOn(PsiMethod method,
                              @Nullable GrExpression caller,
                              GrExpression[] exprs,
                              GrNamedArgument[] namedArgs,
                              GrClosableBlock[] closures,
                              PsiSubstitutor substitutor,
                              GroovyPsiElement context) {
    if (method instanceof GrGdkMethod && !method.hasModifierProperty(GrModifier.STATIC)) {
      if (caller == null) {
        caller = factory.createExpressionFromText("this", context);
      }
      GrExpression[] newArgs = new GrExpression[exprs.length + 1];
      System.arraycopy(exprs, 0, newArgs, 1, exprs.length);
      newArgs[0] = caller;
      invokeMethodOn(((GrGdkMethod)method).getStaticMethod(), null, newArgs, namedArgs, closures, substitutor, context);
      return;
    }

    //todo check for private method?
    if (method.hasModifierProperty(GrModifier.STATIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        builder.append(containingClass.getQualifiedName()).append(".");
      }
    }
    else {
      //LOG.assertTrue(caller != null, "instance method call should have caller");
      if (caller != null) {
        caller.accept(this);
        builder.append(".");
      }
    }
    builder.append(method.getName());
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, substitutor);
    new ArgumentListGenerator(builder, this.context).generate(signature, exprs, namedArgs, closures, context);
  }


  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    //todo infer type parameters from context if possible
    final PsiType type = listOrMap.getType();

    //can be PsiArrayType or GrLiteralClassType
    LOG.assertTrue(type instanceof GrLiteralClassType || type instanceof PsiArrayType);

    String varName = generateListOrMapVariableDeclaration(listOrMap, type);
    generateListOrMapElementInsertions(listOrMap, varName);
    builder.append(varName);
  }

  private void generateListOrMapElementInsertions(GrListOrMap listOrMap, String varName) {
    if (listOrMap.isMap()) {
      for (GrNamedArgument arg : listOrMap.getNamedArguments()) {
        StringBuilder insertion = new StringBuilder();
        insertion.append(varName).append(".put(");
        final String stringKey = arg.getLabelName();
        if (stringKey != null) {
          insertion.append('"').append(stringKey).append('"');
        }
        else {
          final GrArgumentLabel label = arg.getLabel();
          final GrExpression expression = label == null ? null : label.getExpression();
          if (expression != null) {
            expression.accept(new ExpressionGenerator(insertion, context));
          }
          else {
            //todo should we generate an exception?
          }
        }
        insertion.append(", ");
        final GrExpression expression = arg.getExpression();
        if (expression != null) {
          expression.accept(new ExpressionGenerator(insertion, context));
        }
        else {
          //todo should we generate an exception?
        }

        insertion.append(");");
        context.myStatements.add(insertion.toString());
      }
    }
    else {
      for (GrExpression arg : listOrMap.getInitializers()) {
        StringBuilder insertion = new StringBuilder();
        insertion.append(varName).append(".add(");
        arg.accept(new ExpressionGenerator(insertion, context));

        insertion.append(");");
        context.myStatements.add(insertion.toString());
      }

    }
    //todo for list
  }

  private String generateListOrMapVariableDeclaration(GrListOrMap listOrMap, PsiType type) {
    StringBuilder declaration = new StringBuilder();

    writeType(declaration, type);
    final String varName = suggestVarName(type, listOrMap, this.context);
    declaration.append(" ").append(varName).append(" = new ");
    writeType(declaration, type);

    declaration.append("(");
    //insert count of elements in list or map
    declaration.append(listOrMap.isMap() ? listOrMap.getNamedArguments().length : listOrMap.getInitializers().length);
    declaration.append(");");
    context.myStatements.add(declaration.toString());
    return varName;
  }

  @Override
  public void visitRangeExpression(GrRangeExpression range) {
    final PsiType type = range.getType();
    LOG.assertTrue(type instanceof GrRangeType);
    final PsiClass resolved = ((GrRangeType)type).resolve();
    builder.append("new ");
    if (resolved == null) {
      builder.append(GroovyCommonClassNames.GROOVY_LANG_OBJECT_RANGE);
    }
    else {
      builder.append(resolved.getQualifiedName());
    }
    builder.append("(");
    final GrExpression left = range.getLeftOperand();
    left.accept(this);

    builder.append(", ");

    final GrExpression right = range.getRightOperand();
    if (right != null) {
      right.accept(this);
    }

    builder.append(")");
  }
}
