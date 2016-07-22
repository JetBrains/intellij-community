/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.impl.ApplicationStatementUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.method.ExtractMethodInfoHelper;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.*;

/**
 * @author ilyas
 */
public class ExtractUtil {
  private static final Logger LOG = Logger.getInstance(ExtractUtil.class);

  private ExtractUtil() {
  }

  public static GrStatement replaceStatement(@Nullable GrStatementOwner declarationOwner, @NotNull ExtractInfoHelper helper) {
    GrStatement realStatement;
    if (declarationOwner != null && !isSingleExpression(helper.getStatements()) && helper.getStringPartInfo() == null) {
      // Replace set of statements
      final GrStatement[] newStatement = createResultStatement(helper);
      // add call statement
      final GrStatement[] statements = helper.getStatements();
      LOG.assertTrue(statements.length > 0);
      realStatement = null;
      for (GrStatement statement : newStatement) {
        realStatement = declarationOwner.addStatementBefore(statement, statements[0]);
        JavaCodeStyleManager.getInstance(realStatement.getProject()).shortenClassReferences(realStatement);
      }
      LOG.assertTrue(realStatement != null);
      // remove old statements
      removeOldStatements(declarationOwner, helper);
      PsiImplUtil.removeNewLineAfter(realStatement);
    }
    else {
      GrExpression oldExpr;
      if (helper.getStringPartInfo() != null) {
        oldExpr = helper.getStringPartInfo().replaceLiteralWithConcatenation("xyz");
      }
      else {
        oldExpr = (GrExpression)helper.getStatements()[0];
      }

      // Expression call replace
      GrExpression methodCall = createMethodCall(helper);
      realStatement = oldExpr.replaceWithExpression(methodCall, true);
      JavaCodeStyleManager.getInstance(realStatement.getProject()).shortenClassReferences(realStatement);
    }
    return realStatement;
  }

  @NotNull
  private static GrStatement[] createResultStatement(ExtractInfoHelper helper) {
    VariableInfo[] outputVars = helper.getOutputVariableInfos();

    PsiType type = helper.getOutputType();
    GrStatement[] statements = helper.getStatements();
    GrMethodCallExpression callExpression = createMethodCall(helper);

    if ((outputVars.length == 0 || PsiType.VOID.equals(type)) && !helper.hasReturnValue()) return new GrStatement[]{callExpression};
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    if (helper.hasReturnValue()) {
      return new GrStatement[]{factory.createStatementFromText("return " + callExpression.getText())};
    }

    LOG.assertTrue(outputVars.length > 0);

    final List<VariableInfo> mustAdd = mustAddVariableDeclaration(statements, outputVars);
    if (mustAdd.isEmpty()) {
      return new GrStatement[]{
        createAssignment(outputVars, callExpression, helper.getProject())
      };
    }
    else if (mustAdd.size() == outputVars.length && outputVars.length == 1) {
      return new GrVariableDeclaration[]{
        factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, callExpression, outputVars[0].getType(), outputVars[0].getName())
      };
    }
    else if (varsAreEqual(mustAdd, outputVars)) {
      return createTupleDeclaration(outputVars, callExpression, helper.getProject());
    }
    else {
      final List<GrStatement> result = generateVarDeclarations(mustAdd, helper.getProject(), null);
      result.add(createAssignment(outputVars, callExpression, helper.getProject()));
      return result.toArray(new GrStatement[result.size()]);
    }
  }

  private static boolean varsAreEqual(List<VariableInfo> toAdd, VariableInfo[] outputVars) {
    if (toAdd.size() != outputVars.length) return false;
    Set<String> names = ContainerUtil.newHashSet();
    for (VariableInfo info : toAdd) {
      names.add(info.getName());
    }

    for (VariableInfo var : outputVars) {
      if (!names.contains(var.getName())) return false;
    }

    return true;
  }

  private static GrStatement[] createTupleDeclaration(final VariableInfo[] infos,
                                                      GrMethodCallExpression callExpression,
                                                      final Project project) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    StringBuilder tuple = new StringBuilder();
    tuple.append("def (");
    for (VariableInfo info : infos) {
      final PsiType type = info.getType();
      if (type != null) {
        final PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
        tuple.append(unboxed.getCanonicalText());
        tuple.append(' ');
      }
      tuple.append(info.getName());
      tuple.append(",");
    }
    StringUtil.trimEnd(tuple, ",");
    tuple.append(")=");
    tuple.append(callExpression.getText());

    return new GrStatement[]{factory.createStatementFromText(tuple)};
  }

  private static List<GrStatement> generateVarDeclarations(List<VariableInfo> varInfos,
                                                           Project project,
                                                           @Nullable GrExpression initializer) {
    List<GrStatement> result = new ArrayList<>();
    if (varInfos.isEmpty()) return result;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    boolean distinctDeclaration = haveDifferentTypes(varInfos);

    if (distinctDeclaration) {
      for (VariableInfo info : varInfos) {
        result.add(factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, "", info.getType(), info.getName()));
      }
    }
    else {
      String[] names = new String[varInfos.size()];
      for (int i = 0, mustAddLength = varInfos.size(); i < mustAddLength; i++) {
        names[i] = varInfos.get(i).getName();
      }
      result.add(factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, initializer, varInfos.get(0).getType(), names));
    }
    return result;
  }

  private static boolean haveDifferentTypes(List<VariableInfo> varInfos) {
    if (varInfos.size() < 2) return true;
    Set<String> diffTypes = new HashSet<>();
    for (VariableInfo info : varInfos) {
      final PsiType t = info.getType();
      diffTypes.add(t == null ? null : TypesUtil.unboxPrimitiveTypeWrapper(t).getCanonicalText());
    }
    return diffTypes.size() > 1;
  }

  private static GrStatement createAssignment(VariableInfo[] infos, GrMethodCallExpression callExpression, final Project project) {
    StringBuilder text = new StringBuilder();
    if (infos.length > 1) text.append('(');
    for (VariableInfo info : infos) {
      text.append(info.getName()).append(", ");
    }
    if (infos.length > 1) {
      text.replace(text.length() - 2, text.length(), ") =");
    }
    else {
      text.replace(text.length() - 2, text.length(), " = ");
    }
    text.append(callExpression.getText());
    return GroovyPsiElementFactory.getInstance(project).createExpressionFromText(text.toString());
  }

  private static void removeOldStatements(GrStatementOwner owner, ExtractInfoHelper helper) throws IncorrectOperationException {
    owner.removeElements(helper.getInnerElements());
  }

  /*
  To declare or not a variable to which method call result will be assigned.
   */
  private static List<VariableInfo> mustAddVariableDeclaration(@NotNull GrStatement[] statements, @NotNull VariableInfo[] vars) {
    Map<String, VariableInfo> names = new HashMap<>();
    for (VariableInfo var : vars) {
      names.put(var.getName(), var);
    }
    List<VariableInfo> result = new ArrayList<>();

    for (GrStatement statement : statements) {
      if (statement instanceof GrVariableDeclaration) {
        GrVariableDeclaration declaration = (GrVariableDeclaration)statement;
        for (GrVariable variable : declaration.getVariables()) {
          final VariableInfo removed = names.remove(variable.getName());
          if (removed != null) {
            result.add(removed);
          }
        }
      }
    }
    for (String varName : names.keySet()) {
      if (ResolveUtil.resolveProperty(statements[statements.length - 1], varName) == null) {
        result.add(names.get(varName));
      }
    }

    return result;
  }

  public static TextRange getRangeOfRefactoring(ExtractInfoHelper helper) {
    final StringPartInfo stringPartInfo = helper.getStringPartInfo();
    if (stringPartInfo != null) {
      return stringPartInfo.getRange();
    }
    else {
      final GrStatement[] statements = helper.getStatements();
      int start = statements[0].getTextRange().getStartOffset();
      int end = statements[statements.length - 1].getTextRange().getEndOffset();
      return new TextRange(start, end);
    }
  }

  private static Collection<GrVariable> collectUsedLocalVarsOrParamsDeclaredOutside(ExtractInfoHelper helper) {
    final Collection<GrVariable> result = new HashSet<>();

    final TextRange range = getRangeOfRefactoring(helper);
    final int start = range.getStartOffset();
    final int end = range.getEndOffset();

    final GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression ref) {
        final PsiElement resolved = ref.resolve();
        if ((resolved instanceof GrParameter || PsiUtil.isLocalVariable(resolved)) && resolved.isPhysical()) {
          final int offset = resolved.getTextRange().getStartOffset();
          //var is declared outside of selected code
          if (offset < start || end <= offset) {
            result.add((GrVariable)resolved);
          }
        }
      }
    };

    final GrStatement[] statements = helper.getStatements();
    for (GrStatement statement : statements) {
      statement.accept(visitor);
    }

    return result;
  }

  public static GrMethod createMethod(ExtractMethodInfoHelper helper) {
    StringBuilder buffer = new StringBuilder();

    //Add signature
    PsiType type = helper.getOutputType();
    final PsiPrimitiveType outUnboxed = PsiPrimitiveType.getUnboxedType(type);
    if (outUnboxed != null) type = outUnboxed;
    String modifier = getModifierString(helper);
    String typeText = getTypeString(helper, false, modifier);
    buffer.append(modifier);
    buffer.append(typeText);

    appendName(buffer, helper.getName());

    buffer.append("(");
    for (String param : getParameterString(helper, true)) {
      buffer.append(param);
    }
    buffer.append(") { \n");

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    generateBody(helper, PsiType.VOID.equals(type), buffer, helper.isForceReturn());

    buffer.append("\n}");

    String methodText = buffer.toString();
    return factory.createMethodFromText(methodText, helper.getContext());
  }

  public static void appendName(@NotNull final StringBuilder buffer, @NotNull final String name) {
    if (GroovyNamesUtil.isIdentifier(name)) {
      buffer.append(name);
    }
    else {
      buffer.append("'");
      buffer.append(GrStringUtil.escapeSymbolsForString(name, true, false));
      buffer.append("'");
    }
  }

  public static void generateBody(ExtractInfoHelper helper, boolean isVoid, StringBuilder buffer, boolean forceReturn) {
    VariableInfo[] outputInfos = helper.getOutputVariableInfos();

    ParameterInfo[] infos = helper.getParameterInfos();

    Set<String> declaredVars = new HashSet<>();
    for (ParameterInfo info : infos) {
      declaredVars.add(info.getName());
    }

    for (VariableInfo info : mustAddVariableDeclaration(helper.getStatements(), outputInfos)) {
      declaredVars.add(info.getName());
    }

    List<VariableInfo> genDecl = new ArrayList<>();
    final Collection<GrVariable> outside = collectUsedLocalVarsOrParamsDeclaredOutside(helper);

    for (final GrVariable variable : outside) {
      if (!declaredVars.contains(variable.getName())) {
        genDecl.add(new VariableInfo() {
          @NotNull
          @Override
          public String getName() {
            return variable.getName();
          }

          @Override
          public PsiType getType() {
            return variable.getDeclaredType();
          }
        });
      }
    }
    final List<GrStatement> statements = generateVarDeclarations(genDecl, helper.getProject(), null);
    for (GrStatement statement : statements) {
      buffer.append(statement.getText()).append('\n');
    }

    final StringPartInfo stringPartInfo = helper.getStringPartInfo();
    if (!isSingleExpression(helper.getStatements()) && stringPartInfo == null) {
      for (PsiElement element : helper.getInnerElements()) {
        buffer.append(element.getText());
      }
      //append return statement
      if (!isVoid && outputInfos.length > 0) {
        buffer.append('\n');
        if (forceReturn) {
          buffer.append("return ");
        }
        if (outputInfos.length > 1) buffer.append('[');
        for (VariableInfo info : outputInfos) {
          buffer.append(info.getName()).append(", ");
        }
        buffer.delete(buffer.length() - 2, buffer.length());
        if (outputInfos.length > 1) buffer.append(']');
      }
    }
    else {
      GrExpression expr = stringPartInfo != null
                          ? stringPartInfo.createLiteralFromSelected()
                          : (GrExpression)PsiUtil.skipParentheses(helper.getStatements()[0], false);
      boolean addReturn = !isVoid && forceReturn && !PsiUtil.isVoidMethodCall(expr);
      if (addReturn) {
        buffer.append("return ");
        final GrExpression methodCall = ApplicationStatementUtil.convertToMethodCallExpression(expr);
        buffer.append(methodCall.getText());
      }
      else {
        buffer.append(expr != null ? expr.getText() : "");
      }
    }
  }

  public static String[] getParameterString(ExtractInfoHelper helper, boolean useCanonicalText) {
    int i = 0;
    ParameterInfo[] infos = helper.getParameterInfos();
    int number = 0;
    for (ParameterInfo info : infos) {
      if (info.passAsParameter) number++;
    }
    ArrayList<String> params = new ArrayList<>();
    for (ParameterInfo info : infos) {
      if (info.passAsParameter) {
        PsiType paramType = info.type;
        final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(paramType);
        if (unboxed != null) paramType = unboxed;

        String paramTypeText;
        if (paramType == null || paramType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || paramType.equals(PsiType.NULL)) {
          paramTypeText = "";
        }
        else {
          paramTypeText = (useCanonicalText ? paramType.getCanonicalText() : paramType.getPresentableText()) + " ";
        }
        params.add(paramTypeText + info.getName() + (i < number - 1 ? ", " : ""));
        i++;
      }
    }
    return ArrayUtil.toStringArray(params);
  }

  @NotNull
  public static String getTypeString(@NotNull ExtractMethodInfoHelper helper, boolean forPresentation, @NotNull String modifier) {
    if (!helper.specifyType()) {
      return modifier.isEmpty() ? "def " : "";
    }

    PsiType type = helper.getOutputType();
    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;

    final String returnType = StringUtil.notNullize(forPresentation ? type.getPresentableText() : type.getCanonicalText());

    if (StringUtil.isEmptyOrSpaces(returnType) || "null".equals(returnType)) {
      return modifier.isEmpty() ? "def " : "";
    }
    else {
      return returnType + " ";
    }
  }

  public static boolean isSingleExpression(GrStatement[] statements) {
    return statements.length == 1 && statements[0] instanceof GrExpression &&
           !(statements[0].getParent() instanceof GrVariableDeclarationOwner && statements[0] instanceof GrAssignmentExpression);
  }

  private static GrMethodCallExpression createMethodCall(ExtractInfoHelper helper) {
    StringBuilder buffer = new StringBuilder();
    appendName(buffer, helper.getName());
    buffer.append("(");
    int number = 0;
    for (ParameterInfo info : helper.getParameterInfos()) {
      if (info.passAsParameter) number++;
    }
    int i = 0;
    String[] argumentNames = helper.getArgumentNames();
    for (String argName : argumentNames) {
      if (!argName.isEmpty()) {
        buffer.append(argName);
        if (i < number - 1) {
          buffer.append(",");
        }
        i++;
      }
    }

    buffer.append(")");
    String callText = buffer.toString();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    GrExpression expr = factory.createExpressionFromText(callText);
    LOG.assertTrue(expr instanceof GrMethodCallExpression, callText);
    return ((GrMethodCallExpression)expr);
  }

  public static int getCaretOffset(@NotNull GrStatement statement) {
    if (statement instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration)statement).getVariables();
      if (variables.length > 0) {
        GrExpression initializer = variables[0].getInitializerGroovy();
        if (initializer != null) {
          return initializer.getTextOffset();
        }
      }
    }
    else if (statement instanceof GrAssignmentExpression) {
      GrExpression value = ((GrAssignmentExpression)statement).getRValue();
      if (value != null) {
        return value.getTextOffset();
      }
    }
    return statement.getTextOffset();
  }

  public static String getModifierString(ExtractMethodInfoHelper helper) {
    String visibility = helper.getVisibility();
    LOG.assertTrue(visibility != null && !visibility.isEmpty());
    final StringBuilder builder = new StringBuilder();
    builder.append(visibility);
    builder.append(" ");
    if (helper.isStatic()) {
      builder.append("static ");
    }
    return builder.toString();
  }
}
