/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMapToClassIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.conversions.ConvertMapToClassIntention");

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull final Project project, Editor editor) throws IncorrectOperationException {
    final GrListOrMap map = (GrListOrMap)element;
    final GrNamedArgument[] namedArguments = map.getNamedArguments();
    LOG.assertTrue(map.getInitializers().length == 0);
    final PsiFile file = map.getContainingFile();
    final String packageName = file instanceof GroovyFileBase ? ((GroovyFileBase)file).getPackageName() : "";

    final CreateClassDialog dialog =
      new CreateClassDialog(project, GroovyBundle.message("create.class.family.name"), "", packageName, GrCreateClassKind.CLASS, true,
                            ModuleUtilCore.findModuleForPsiElement(element));
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

    boolean replaceReturnType = checkForReturnFromMethod(map);
    boolean variableDeclaration = checkForVariableDeclaration(map);
    final GrParameter methodParameter = checkForMethodParameter(map);

    final String qualifiedClassName = dialog.getClassName();
    final String selectedPackageName = StringUtil.getPackageName(qualifiedClassName);
    final String shortName = StringUtil.getShortName(qualifiedClassName);

    final GrTypeDefinition typeDefinition = createClass(project, namedArguments, selectedPackageName, shortName);
    WriteAction.run(() -> {
      PsiClass generatedClass = CreateClassActionBase.createClassByType(
        dialog.getTargetDirectory(), typeDefinition.getName(), PsiManager.getInstance(project), map, GroovyTemplates.GROOVY_CLASS, true);
      PsiClass replaced = (PsiClass)generatedClass.replace(typeDefinition);
      replaceMapWithClass(project, map, replaced, replaceReturnType, variableDeclaration, methodParameter);
    });
  }

  public static void replaceMapWithClass(Project project,
                                         final GrListOrMap map,
                                         PsiClass generatedClass,
                                         boolean replaceReturnType,
                                         boolean variableDeclaration,
                                         GrParameter parameter) {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(generatedClass);

    final String text = map.getText();
    int begin = 0;
    int end = text.length();
    if (text.startsWith("[")) begin++;
    if (text.endsWith("]")) end--;
    final GrExpression newExpression = GroovyPsiElementFactory.getInstance(project)
      .createExpressionFromText("new " + generatedClass.getQualifiedName() + "(" + text.substring(begin, end) + ")");
    final GrExpression replacedNewExpression = ((GrExpression)map.replace(newExpression));

    if (replaceReturnType) {
      final PsiType type = replacedNewExpression.getType();
      final GrMethod method = PsiTreeUtil.getParentOfType(replacedNewExpression, GrMethod.class, true, GrClosableBlock.class);
      LOG.assertTrue(method != null);
      GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(type));
    }

    if (variableDeclaration) {
      final PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
      ((GrVariable)parent).setType(replacedNewExpression.getType());
    }
    if (parameter != null) {
      parameter.setType(newExpression.getType());
    }

    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacedNewExpression);

    IntentionUtils.positionCursor(project, generatedClass.getContainingFile(), generatedClass);
  }

  public static boolean checkForReturnFromMethod(GrExpression replacedNewExpression) {
    final PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
    final GrMethod method = PsiTreeUtil.getParentOfType(replacedNewExpression, GrMethod.class, true, GrClosableBlock.class);
    if (method == null) return false;

    if (!(parent instanceof GrReturnStatement)) { //check for return expression
      final List<GrStatement> returns = ControlFlowUtils.collectReturns(method.getBlock());
      final PsiElement expr = PsiUtil.skipParentheses(replacedNewExpression, true);
      if (!(returns.contains(expr))) return false;
    }
    return !(!ApplicationManager.getApplication().isUnitTestMode() && Messages.showYesNoDialog(replacedNewExpression.getProject(),
                                                                                               GroovyIntentionsBundle.message(
                                                                                                 "do.you.want.to.change.method.return.type",
                                                                                                 method.getName()),
                                                                                               GroovyIntentionsBundle
                                                                                                 .message(
                                                                                                   "convert.map.to.class.intention.name"),
                                                                                               Messages.getQuestionIcon()) != Messages.YES);
  }

  public static boolean checkForVariableDeclaration(GrExpression replacedNewExpression) {
    final PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
    if (parent instanceof GrVariable &&
        !(parent instanceof GrField) &&
        !(parent instanceof GrParameter) &&
        ((GrVariable)parent).getDeclaredType() != null &&
        replacedNewExpression.getType() != null) {
      if (ApplicationManager.getApplication().isUnitTestMode() || Messages.showYesNoDialog(replacedNewExpression.getProject(),
                                                                                            GroovyIntentionsBundle.message(
                                                                                              "do.you.want.to.change.variable.type",
                                                                                              ((GrVariable)parent).getName()),
                                                                                            GroovyIntentionsBundle.message(
                                                                                              "convert.map.to.class.intention.name"),
                                                                                            Messages.getQuestionIcon()) ==
                                                                  Messages.YES) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static GrParameter getParameterByArgument(GrExpression arg) {
    PsiElement parent = PsiUtil.skipParentheses(arg.getParent(), true);
    if (!(parent instanceof GrArgumentList)) return null;
    final GrArgumentList argList = (GrArgumentList)parent;

    parent = parent.getParent();
    if (!(parent instanceof GrMethodCall)) return null;

    final GrMethodCall methodCall = (GrMethodCall)parent;
    final GrExpression expression = methodCall.getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return null;

    final GroovyResolveResult resolveResult = ((GrReferenceExpression)expression).advancedResolve();
    if (resolveResult == null) return null;

    GrClosableBlock[] closures = methodCall.getClosureArguments();
    final Map<GrExpression, Pair<PsiParameter, PsiType>> mapToParams = GrClosureSignatureUtil
      .mapArgumentsToParameters(resolveResult, arg, false, false, argList.getNamedArguments(), argList.getExpressionArguments(), closures);
    if (mapToParams == null) return null;

    final Pair<PsiParameter, PsiType> parameterPair = mapToParams.get(arg);
    final PsiParameter parameter = parameterPair == null ? null : parameterPair.getFirst();

    return parameter instanceof GrParameter? ((GrParameter)parameter):null;
  }

  @Nullable
  public static GrParameter checkForMethodParameter(GrExpression map) {
    final GrParameter parameter = getParameterByArgument(map);
    if (parameter == null) return null;
    final PsiElement parent = parameter.getParent().getParent();
    if (!(parent instanceof PsiMethod)) return null;
    final PsiMethod method = (PsiMethod)parent;
    if (ApplicationManager.getApplication().isUnitTestMode() ||
           Messages.showYesNoDialog(map.getProject(), GroovyIntentionsBundle
             .message("do.you.want.to.change.type.of.parameter.in.method", parameter.getName(), method.getName()),
                                    GroovyIntentionsBundle.message("convert.map.to.class.intention.name"), Messages.getQuestionIcon()) == Messages.YES) {
      return parameter;
    }
    return null;
  }


  public static GrTypeDefinition createClass(Project project, GrNamedArgument[] namedArguments, String packageName, String className) {
    StringBuilder classText = new StringBuilder();
    if (!packageName.isEmpty()) {
      classText.append("package ").append(packageName).append('\n');
    }
    classText.append("class ").append(className).append(" {\n");
    for (GrNamedArgument argument : namedArguments) {
      final String fieldName = argument.getLabelName();
      final GrExpression expression = argument.getExpression();
      LOG.assertTrue(expression != null);

      final PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(expression.getType());
      if (type != null) {
        classText.append(type.getCanonicalText());
      }
      else {
        classText.append(GrModifier.DEF);
      }
      classText.append(' ').append(fieldName).append('\n');
    }
    classText.append('}');
    return GroovyPsiElementFactory.getInstance(project).createTypeDefinition(classText.toString());
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }
}

class MyPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrListOrMap)) return false;
    final GrListOrMap map = (GrListOrMap)element;
    final GrNamedArgument[] namedArguments = map.getNamedArguments();
    final GrExpression[] initializers = map.getInitializers();
    if (initializers.length != 0) return false;

    for (GrNamedArgument argument : namedArguments) {
      final GrArgumentLabel label = argument.getLabel();
      final GrExpression expression = argument.getExpression();
      if (label == null || expression == null) return false;
      if (label.getName() == null) return false;
    }
    return true;
  }
}
