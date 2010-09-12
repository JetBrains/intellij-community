/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMapToClassIntention extends Intention {
  private static Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.conversions.ConvertMapToClassIntention");

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrListOrMap map = (GrListOrMap)element;
    final GrNamedArgument[] namedArguments = map.getNamedArguments();
    LOG.assertTrue(map.getInitializers().length == 0);
    final String packageName = ((GroovyFileBase)map.getContainingFile()).getPackageName();

    final CreateClassDialog dialog =
      new CreateClassDialog(project, GroovyBundle.message("create.class.family.name"), "", packageName, CreateClassKind.CLASS, true,
                            ModuleUtil.findModuleForPsiElement(element));
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

    final String qualifiedClassName = dialog.getClassName();
    final String selectedPackageName = StringUtil.getPackageName(qualifiedClassName);
    final String shortName = StringUtil.getShortName(qualifiedClassName);

    final GrTypeDefinition typeDefinition = createClass(project, namedArguments, selectedPackageName, shortName);
    final PsiClass generatedClass = CreateClassActionBase.createClassByType(
      dialog.getTargetDirectory(), typeDefinition.getName(), PsiManager.getInstance(project), map);
    final PsiClass replaced = (PsiClass)generatedClass.replace(typeDefinition);
    replaceMapWithClass(project, map, replaced);
  }

  public static void replaceMapWithClass(Project project, GrListOrMap map, PsiClass generatedClass) {
    PsiUtil.shortenReferences((GroovyPsiElement)generatedClass);
    CreateClassActionBase.putCursor(project, generatedClass.getContainingFile(), generatedClass);

    final String text = map.getText();
    int begin = 0;
    int end = text.length();
    if (text.startsWith("[")) begin++;
    if (text.endsWith("]")) end--;
    final GrExpression newExpression = GroovyPsiElementFactory.getInstance(project)
      .createExpressionFromText("new " + generatedClass.getQualifiedName() + "(" + text.substring(begin, end) + ")");
    final GrExpression replacedNewExpression = ((GrExpression)map.replace(newExpression));
    checkForVariableDeclaration(replacedNewExpression);
    checkForReturnFromMethod(replacedNewExpression);
    PsiUtil.shortenReferences(replacedNewExpression);
  }

  private static void checkForReturnFromMethod(GrExpression replacedNewExpression) {
    final PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
    final GrMethod method = PsiTreeUtil.getParentOfType(replacedNewExpression, GrMethod.class, true, GrClosableBlock.class);
    if (method == null) return;

    final List<GrStatement> returns = ControlFlowUtils.collectReturns(method.getBlock());
    if (returns.size() > 1 || !returns.contains(parent)) return;

    if (method.getReturnTypeElementGroovy() == null) return;
    final PsiType returnType = method.getReturnType();
    final PsiType type = replacedNewExpression.getType();
    if (TypesUtil.isAssignable(returnType, type, replacedNewExpression.getManager(), replacedNewExpression.getResolveScope())) return;

    method.setReturnType(type);
  }

  private static void checkForVariableDeclaration(GrExpression replacedNewExpression) {
    final PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
    if (parent instanceof GrVariable &&
        !(parent instanceof GrField) &&
        !(parent instanceof GrParameter) &&
        ((GrVariable)parent).getDeclaredType() != null &&
        replacedNewExpression.getType() != null) {
      ((GrVariable)parent).setType(replacedNewExpression.getType());
    }
  }

  public static GrTypeDefinition createClass(Project project, GrNamedArgument[] namedArguments, String packageName, String className) {
    StringBuilder classText = new StringBuilder();
    if (packageName.length() > 0) {
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
  public boolean satisfiedBy(PsiElement element) {
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
