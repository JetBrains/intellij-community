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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBSR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSL_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.TypeWriter.writeType;

/**
 * @author Maxim.Medvedev
 */
public class GenerationUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil");

  private GenerationUtil() {
  }

  public static void writeTypeParameters(StringBuilder builder,
                                          PsiType[] parameters,
                                          PsiElement context,
                                          ClassNameProvider classNameProvider) {
    if (parameters.length == 0) return;

    builder.append('<');
    for (PsiType parameter : parameters) {
      if (parameter instanceof PsiPrimitiveType) {
        parameter = TypesUtil.boxPrimitiveType(parameter, context.getManager(), context.getResolveScope(), true);
      }
      writeType(builder, parameter, context, classNameProvider);
      builder.append(", ");
    }
    builder.delete(builder.length()-2, builder.length()).append('>');
    //builder.removeFromTheEnd(2).append('>');
  }

  static String suggestVarName(GrExpression expr, ExpressionContext expressionContext) {
    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(expr, expressionContext.myUsedVarNames, true);
    final String[] varNames = GroovyNameSuggestionUtil.suggestVariableNames(expr, nameValidator);

    LOG.assertTrue(varNames.length > 0);
    expressionContext.myUsedVarNames.add(varNames[0]);
    return varNames[0];
  }

  static String suggestVarName(PsiType type, GroovyPsiElement context, ExpressionContext expressionContext) {
    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(context, expressionContext.myUsedVarNames, true, true);
    if (type instanceof PsiPrimitiveType) type = TypesUtil.boxPrimitiveType(type, context.getManager(), context.getResolveScope());
    final String[] varNames = GroovyNameSuggestionUtil.suggestVariableNameByType(type, nameValidator);

    LOG.assertTrue(varNames.length > 0);
    expressionContext.myUsedVarNames.add(varNames[0]);
    return varNames[0];
  }

  public static String validateName(String name, GroovyPsiElement context, ExpressionContext expressionContext) {
    return new DefaultGroovyVariableNameValidator(context, expressionContext.myUsedVarNames, true).validateName(name, true);
  }

  public static void writeCodeReferenceElement(StringBuilder builder, GrCodeReferenceElement referenceElement) {
    final GroovyResolveResult resolveResult = referenceElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    if (resolved == null) {
      builder.append(referenceElement.getText());
      return;
    }
    LOG.assertTrue(resolved instanceof PsiClass || resolved instanceof PsiPackage);
    if (resolved instanceof PsiClass) {
      builder.append(((PsiClass)resolved).getQualifiedName());
    }
    else {
      builder.append(((PsiPackage)resolved).getQualifiedName());
    }
    writeTypeParameters(builder, referenceElement.getTypeArguments(), referenceElement, new GeneratorClassNameProvider());
  }

  public static void invokeMethodByName(@Nullable GrExpression caller,
                                        String methodName,
                                        GrExpression[] exprs,
                                        GrNamedArgument[] namedArgs,
                                        GrClosableBlock[] closureArgs,
                                        ExpressionGenerator expressionGenerator,
                                        GroovyPsiElement psiContext) {
    GroovyResolveResult call = resolveMethod(caller, methodName, exprs, namedArgs, closureArgs, psiContext);
    invokeMethodByResolveResult(caller, call, methodName, exprs, namedArgs, closureArgs, expressionGenerator, psiContext);
  }

  public static GroovyResolveResult resolveMethod(@Nullable GrExpression caller,
                                                   String methodName,
                                                   GrExpression[] exprs,
                                                   GrNamedArgument[] namedArgs,
                                                   GrClosableBlock[] closureArgs,
                                                   GroovyPsiElement psiContext) {
    GroovyResolveResult call = GroovyResolveResult.EMPTY_RESULT;

    final PsiType type;
    if (caller == null) {
      type = GroovyPsiElementFactory.getInstance(psiContext.getProject()).createExpressionFromText("this", psiContext).getType();
    }
    else {
      type = caller.getType();
    }
    if (type != null) {
      final PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprs, closureArgs, false, null, false);
      final GroovyResolveResult[] candidates = ResolveUtil.getMethodCandidates(type, methodName, psiContext, argumentTypes);
      call = PsiImplUtil.extractUniqueResult(candidates);
    }
    return call;
  }

  public static void invokeMethodByResolveResult(@Nullable GrExpression caller,
                                                 GroovyResolveResult resolveResult,
                                                 String methodName,
                                                 GrExpression[] exprs,
                                                 GrNamedArgument[] namedArgs,
                                                 GrClosableBlock[] closureArgs,
                                                 ExpressionGenerator expressionGenerator,
                                                 GroovyPsiElement psiContext) {
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      expressionGenerator.invokeMethodOn(((PsiMethod)resolved), caller, exprs, namedArgs, closureArgs, substitutor, psiContext);
      return;
    }
    //other case
    final StringBuilder builder = expressionGenerator.getBuilder();
    final ExpressionContext expressionContext = expressionGenerator.getContext();

    if (caller != null) {
      caller.accept(expressionGenerator);
      builder.append('.');
    }
    builder.append(methodName);
    final ArgumentListGenerator argumentListGenerator = new ArgumentListGenerator(builder, expressionContext);
    argumentListGenerator.generate(null, exprs, namedArgs, closureArgs, psiContext);
  }

  static void writeStatement(final StringBuilder codeBlockBuilder,
                             StringBuilder statementBuilder,
                             @Nullable GrStatement statement,
                             @Nullable ExpressionContext context) {
    final PsiElement parent = statement == null ? null : statement.getParent();

    final boolean addParentheses;
    if (statement == null) {
      addParentheses = context != null && context.shouldInsertCurlyBrackets();
    }
    else {
      addParentheses =
        context != null && (context.shouldInsertCurlyBrackets() || context.myStatements.size() > 0) && parent instanceof GrControlStatement;
    }

    if (addParentheses) {
      codeBlockBuilder.append("{\n");
    }

    if (context != null) {
      insertStatementFromContextBefore(codeBlockBuilder, context);
    }
    codeBlockBuilder.append(statementBuilder);
    if (addParentheses) {
      codeBlockBuilder.append("}\n");
    }
  }

  public static void insertStatementFromContextBefore(StringBuilder codeBlockBuilder, ExpressionContext context) {
    for (String st : context.myStatements) {
      codeBlockBuilder.append(st).append('\n');
    }
  }

  public static void writeStatement(final StringBuilder builder, ExpressionContext context, @Nullable GrStatement statement, StatementWriter writer) {
    StringBuilder statementBuilder = new StringBuilder();
    ExpressionContext statementContext = context.copy();
    writer.writeStatement(statementBuilder, statementContext);
    writeStatement(builder, statementBuilder, statement, statementContext);
  }

  @Nullable
  static PsiClass findAccessibleSuperClass(@NotNull PsiElement context, @NotNull PsiClass initialClass) {
    Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
    PsiClass curClass = initialClass;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();

    while (curClass != null && !resolveHelper.isAccessible(curClass, context, null)) {
      curClass = curClass.getSuperClass();
      if (visitedClasses.contains(curClass)) return null;
      visitedClasses.add(curClass);
    }
    return curClass;
  }

  static boolean isAbstractInJava(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }

    final PsiClass psiClass = method.getContainingClass();
    return psiClass != null && GrClassSubstitutor.getSubstitutedClass(psiClass).isInterface();
  }

  static void writeTypeParameters(StringBuilder text,
                                  PsiTypeParameterListOwner typeParameterListOwner,
                                  final ClassNameProvider classNameProvider) {
    if (!typeParameterListOwner.hasTypeParameters()) return;

    text.append('<');
    PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
    final PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) text.append(", ");
      PsiTypeParameter parameter = parameters[i];
      text.append(parameter.getName());
      PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length > 0) {
        text.append(" extends ");
        for (int j = 0; j < extendsListTypes.length; j++) {
          if (j > 0) text.append(" & ");
          writeType(text, extendsListTypes[j], typeParameterList, classNameProvider);
        }
      }
    }
    text.append('>');
  }

  static void writeParameterList(@NotNull StringBuilder text,
                                 @NotNull PsiParameter[] parameters,
                                 @NotNull final ClassNameProvider classNameProvider,
                                 @Nullable ExpressionContext context) {
    Set<String> usedNames = new HashSet<String>();
    text.append('(');

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;
      if (parameter instanceof PsiCompiledElement) parameter = (PsiParameter)((PsiCompiledElement)parameter).getMirror();

      if (i > 0) text.append(", ");  //append ','
      if (!classNameProvider.forStubs()) {
        ModifierListGenerator.writeModifiers(text, parameter.getModifierList(), ModifierListGenerator.JAVA_MODIFIERS, true);
      }
      if (context != null) {
        if (context.analyzedVars.toMakeFinal(parameter) && !parameter.hasModifierProperty(PsiModifier.FINAL)) {
          text.append(PsiModifier.FINAL).append(' ');
        }
        writeType(text, context.typeProvider.getParameterType(parameter), parameter, classNameProvider);
      }
      else {
        writeType(text, parameter.getType(), parameter, classNameProvider);
      }
      text.append(' ');
      text.append(generateUniqueName(usedNames, parameter.getName()));

      i++;
    }
    text.append(')');
    text.append(' ');
  }

  private static String generateUniqueName(Set<String> usedNames, String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      name = "p";
    }
    while (!usedNames.add(name)) {
      name += "x";
    }
    return name;
  }

  static void writeThrowsList(StringBuilder text,
                              PsiReferenceList throwsList,
                              PsiClassType[] exceptions,
                              final ClassNameProvider classNameProvider) {
    if (exceptions.length <= 0) return;

    text.append("throws ");
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (i != 0) {
        text.append(',');
      }
      writeType(text, exception, throwsList, classNameProvider);
      text.append(' ');
    }
  }

  static Set<String> getVarTypes(GrVariableDeclaration variableDeclaration) {
    GrVariable[] variables = variableDeclaration.getVariables();
    final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovy();
    Set<String> types = new HashSet<String>(variables.length);
    if (typeElement == null) {
      if (variables.length > 1) {
        for (GrVariable variable : variables) {
          final GrExpression initializer = variable.getInitializerGroovy();
          if (initializer != null) {
            final PsiType varType = initializer.getType();
            if (varType != null) {
              types.add(getTypeText(varType, variableDeclaration));
            }
          }
        }
      }
    }
    return types;
  }

  static String getTypeText(PsiType varType, PsiElement context) {
    final StringBuilder builder = new StringBuilder();
    writeType(builder, varType, context);
    return builder.toString();
  }

  static ArrayList<GrParameter> getActualParams(GrMethod constructor, int skipOptional) {
    GrParameter[] parameterList = constructor.getParameters();
    return getActualParams(parameterList, skipOptional);
  }

  public static ArrayList<GrParameter> getActualParams(GrParameter[] parameters, int skipOptional) {
    final ArrayList<GrParameter> actual = new ArrayList<GrParameter>(Arrays.asList(parameters));
    if (skipOptional == 0) return actual;
    for (int i = parameters.length - 1; i >= 0; i--) {
      if (!actual.get(i).isOptional()) continue;

      actual.remove(i);
      skipOptional--;
      if (skipOptional == 0) break;
    }
    return actual;
  }

  public static void writeSimpleVarDeclaration(GrVariableDeclaration variableDeclaration,
                                               StringBuilder builder,
                                               ExpressionContext expressionContext) {
    GrVariable[] variables = variableDeclaration.getVariables();

    //Set<String> types = getVarTypes(variableDeclaration);

    //if (types.size() > 1) {
    if (variables.length > 1 && variableDeclaration.getParent() instanceof GrControlStatement) {
        expressionContext.setInsertCurlyBrackets();
      }
      for (GrVariable variable : variables) {
        writeVariableSeparately(variable, builder, expressionContext);
        builder.append(";\n");
      }
      builder.delete(builder.length()-1, builder.length());
      //builder.removeFromTheEnd(1);
      /*return;
    }


    ModifierListGenerator.writeModifiers(builder, variableDeclaration.getModifierList());

    PsiType type = getVarType(variables[0]);
    writeType(builder, type, variableDeclaration);

    builder.append(" ");
    for (GrVariable variable : variables) {
      writeVariableWithoutType(builder, expressionContext, variable);
      builder.append(", ");
    }
    if (variables.length > 0) {
      builder.delete(builder.length() - 2, builder.length());
    }
    builder.append(";");*/
  }

  static void writeVariableWithoutType(StringBuilder builder,
                                       ExpressionContext expressionContext,
                                       GrVariable variable,
                                       boolean wrapped,
                                       PsiType original) {
    builder.append(variable.getName());
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer != null) {
      builder.append(" = ");
      if (wrapped) {
        builder.append("new ").append(GroovyCommonClassNames.GROOVY_LANG_REFERENCE);
        if (original != null) {
          builder.append('<');
          writeType(builder, original, variable, new GeneratorClassNameProvider());
          builder.append('>');
        }
        builder.append('(');
      }
      final PsiType iType = getDeclaredType(initializer, expressionContext);

      //generate cast
      if (original != null && iType != null && !TypesUtil.isAssignable(original, iType, initializer)) {
        builder.append('(');
        writeType(builder, original, initializer);
        builder.append(')');
      }

      initializer.accept(new ExpressionGenerator(builder, expressionContext));
      if (wrapped) {
        builder.append(')');
      }
    }
  }

  static void writeVariableSeparately(GrVariable variable, StringBuilder builder, ExpressionContext expressionContext) {
    PsiType type = expressionContext.typeProvider.getVarType(variable);
    ModifierListGenerator.writeModifiers(builder, variable.getModifierList());

    PsiType originalType = type;
    LocalVarAnalyzer.Result analyzedVars = expressionContext.analyzedVars;
    boolean wrapped = false;
    if (analyzedVars != null) {
      if (analyzedVars.toMakeFinal(variable) && !variable.hasModifierProperty(PsiModifier.FINAL)) {
        builder.append(PsiModifier.FINAL).append(' ');
      }
      else if (analyzedVars.toWrap(variable)) {
        builder.append(PsiModifier.FINAL).append(' ');
        type = JavaPsiFacade.getElementFactory(expressionContext.project).createTypeFromText(
          GroovyCommonClassNames.GROOVY_LANG_REFERENCE + "<" + getTypeText(originalType, variable) + ">", variable);
        wrapped = true;
      }
    }

    writeType(builder, type, variable);
    builder.append(' ');

    writeVariableWithoutType(builder, expressionContext, variable, wrapped, originalType);
  }

  private static final Map<IElementType, Pair<String, IElementType>> binOpTypes = new HashMap<IElementType, Pair<String, IElementType>>();

  static {
    binOpTypes.put(mPLUS_ASSIGN, new Pair<String, IElementType>("+", mPLUS));
    binOpTypes.put(mMINUS_ASSIGN, new Pair<String, IElementType>("-", mMINUS));
    binOpTypes.put(mSTAR_ASSIGN, new Pair<String, IElementType>("*", mSTAR));
    binOpTypes.put(mDIV_ASSIGN, new Pair<String, IElementType>("/", mDIV));
    binOpTypes.put(mMOD_ASSIGN, new Pair<String, IElementType>("%", mMOD));
    binOpTypes.put(mSL_ASSIGN, new Pair<String, IElementType>("<<", COMPOSITE_LSHIFT_SIGN));
    binOpTypes.put(mSR_ASSIGN, new Pair<String, IElementType>(">>", COMPOSITE_RSHIFT_SIGN));
    binOpTypes.put(mBSR_ASSIGN, new Pair<String, IElementType>(">>>", COMPOSITE_TRIPLE_SHIFT_SIGN));
    binOpTypes.put(mBAND_ASSIGN, new Pair<String, IElementType>("&", mBAND));
    binOpTypes.put(mBOR_ASSIGN, new Pair<String, IElementType>("|", mBOR));
    binOpTypes.put(mBXOR_ASSIGN, new Pair<String, IElementType>("^", mBXOR));
    binOpTypes.put(mSTAR_STAR_ASSIGN, new Pair<String, IElementType>("**", mSTAR_STAR));
  }

  public static Pair<String, IElementType> getBinaryOperatorType(IElementType op_assign) {
    return binOpTypes.get(op_assign);
  }

  public static String suggestMethodName(GroovyPsiElement place, String initialName, ExpressionContext context) {
    int count = 0;
    String name = initialName;
    Class[] classes = {PsiMethod.class};
    final Map<PsiMethod, String> setters = context.getSetters();
    while (setters.containsValue(name) || ResolveUtil.resolveExistingElement(
        place,
        new MethodResolverProcessor(name, place, false, null, null, null, true, true),
        classes) != null) {
      name = initialName + count;
      count++;
    }
    return name;
  }

  public static boolean isCastNeeded(@NotNull GrExpression qualifier, @NotNull final PsiMember member, ExpressionContext context) {
    PsiType declared = getDeclaredType(qualifier, context);
    if (declared == null) return false;

    final PsiManager manager = PsiManager.getInstance(context.project);
    return ResolveUtil.processAllDeclarations(declared, new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if (manager.areElementsEquivalent(element, member)) return false;
        return true;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, Object associated) {
      }
    }, ResolveState.initial(), qualifier);
  }

  @Nullable
  public static PsiType getDeclaredType(@Nullable GrExpression expression, ExpressionContext context) {
    if (expression instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)expression).advancedResolve();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiVariable) {
        return substitutor.substitute(context.typeProvider.getVarType((PsiVariable)resolved));
      }
      else if (resolved instanceof PsiMethod) {
        return getDeclaredType((PsiMethod)resolved, substitutor, context);
      }
    }
    else if (expression instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)expression).getInvokedExpression();
      return getDeclaredType(invokedExpression, context);
    }
    else if (expression instanceof GrBinaryExpression) {
      final GroovyResolveResult result = PsiImplUtil.extractUniqueResult(((GrBinaryExpression)expression).multiResolve(false));
      if (result.getElement() instanceof PsiMethod) {
        return getDeclaredType((PsiMethod)result.getElement(), result.getSubstitutor(), context);
      }
    }
    else if (expression instanceof GrIndexProperty) {
      final GroovyResolveResult result = ((GrIndexProperty)expression).advancedResolve();
      if (result.getElement() instanceof PsiMethod) {
        return getDeclaredType((PsiMethod)result.getElement(), result.getSubstitutor(), context);
      }
    }
    else if (expression instanceof GrAssignmentExpression) {
      return getDeclaredType(((GrAssignmentExpression)expression).getRValue(), context);
    }
    else if (expression instanceof GrConditionalExpression) {
      return TypesUtil.getLeastUpperBoundNullable(getDeclaredType(((GrConditionalExpression)expression).getThenBranch(), context),
                                                  getDeclaredType(((GrConditionalExpression)expression).getElseBranch(), context),
                                                  expression.getManager());
    }
    else if (expression instanceof GrParenthesizedExpression) {
      return getDeclaredType(((GrParenthesizedExpression)expression).getOperand(), context);
    }
    else if (expression == null) {
      return null;
    }
    return expression.getType();
  }

  public static PsiType getDeclaredType(PsiMethod method, PsiSubstitutor substitutor, ExpressionContext context) {
    if (method instanceof GrGdkMethod) method = ((GrGdkMethod)method).getStaticMethod();
    if (context.isClassConverted(method.getContainingClass())) {
      return substitutor.substitute(PsiUtil.getSmartReturnType(method));
    }
    else {
      return substitutor.substitute(method.getReturnType());
    }
  }

  public static void wrapInCastIfNeeded(StringBuilder builder,
                                        @NotNull PsiType expected,
                                        @Nullable PsiType actual,
                                        GroovyPsiElement context,
                                        ExpressionContext expressionContext,
                                        StatementWriter writer) {
    if (actual != null && TypesUtil.isAssignable(expected, actual, context) || expected.equalsToText(CommonClassNames.JAVA_LANG_OBJECT))  {
      writer.writeStatement(builder, expressionContext);
    }
    else {
      wrapInCast(builder, expected, context, expressionContext, writer);
    }
  }

  private static void wrapInCast(StringBuilder builder,
                                 @NotNull PsiType expected,
                                 PsiElement context,
                                 ExpressionContext expressionContext,
                                 StatementWriter writer) {
    builder.append("((");

    //todo check operator priority IDEA-93790
    writeType(builder, expected, context);
    builder.append(")(") ;
    writer.writeStatement(builder, expressionContext);
    builder.append("))");
  }

  static PsiType getNotNullType(PsiElement context, PsiType type) {
    return type != null ? type : TypesUtil.getJavaLangObject(context);
  }

  public static void writeThisReference(@Nullable PsiClass targetClass, StringBuilder buffer, ExpressionContext context) {
    if (targetClass != null && !(targetClass instanceof PsiAnonymousClass)) {
      final GrCodeReferenceElement ref = GroovyPsiElementFactory.getInstance(context.project).createCodeReferenceElementFromClass(targetClass);
      writeCodeReferenceElement(buffer, ref);
      buffer.append('.');
    }
    buffer.append("this");
  }

  public static void writeSuperReference(@Nullable PsiClass targetClass, StringBuilder buffer, ExpressionContext context) {
    if (targetClass != null && !(targetClass instanceof PsiAnonymousClass)) {
      final GrCodeReferenceElement ref = GroovyPsiElementFactory.getInstance(context.project).createCodeReferenceElementFromClass(targetClass);
      writeCodeReferenceElement(buffer, ref);
      buffer.append('.');
    }
    buffer.append("super");
  }

  @Nullable
  static PsiElement getWrappingImplicitClass(@NotNull PsiElement place) {
    PsiElement parent = place.getParent();

    while (parent != null) {
      if (parent instanceof PsiClass) return null;
      if (parent instanceof GroovyFile && !(PsiUtil.isInDummyFile(parent))) return null;

      if (parent instanceof GrClosableBlock) return parent;

      parent = parent.getContext();
    }

    return null;
  }
}
