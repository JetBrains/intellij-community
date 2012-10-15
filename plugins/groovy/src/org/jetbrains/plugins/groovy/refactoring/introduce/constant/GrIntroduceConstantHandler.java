/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantHandler extends GrIntroduceHandlerBase<GrIntroduceConstantSettings> {
  public static final String REFACTORING_NAME = "Introduce Constant";

  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  @NotNull
  @Override
  protected PsiElement findScope(GrExpression expression, GrVariable variable) {
    final PsiElement place = expression == null ? variable : expression;
    return place.getContainingFile();
  }

  @Override
  protected void checkExpression(GrExpression selectedExpr) {
    selectedExpr.accept(new ConstantChecker(selectedExpr, selectedExpr));
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrRefactoringError {
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer == null) {
      throw new GrRefactoringError(RefactoringBundle.message("variable.does.not.have.an.initializer", variable.getName()));
    }
    checkExpression(initializer);
  }

  @Override
  protected void checkOccurrences(PsiElement[] occurrences) {
    if (hasLhs(occurrences)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.variable.is.used.for.write"));
    }
  }

  @Nullable
  public static GrTypeDefinition findContainingClass(GrIntroduceContext context) {
    PsiElement place = context.getPlace();
    while (true) {
      final GrTypeDefinition typeDefinition = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class, true, GroovyFileBase.class);
      if (typeDefinition == null) return null;
      if (!typeDefinition.isAnonymous() &&
          (typeDefinition.hasModifierProperty(PsiModifier.STATIC) || typeDefinition.getContainingClass() == null)) {
        return typeDefinition;
      }
      place = typeDefinition;
    }
  }


  @Override
  protected GrIntroduceDialog<GrIntroduceConstantSettings> getDialog(GrIntroduceContext context) {
    return new GrIntroduceConstantDialog(context, findContainingClass(context));
  }

  @Override
  public GrField runRefactoring(GrIntroduceContext context, GrIntroduceConstantSettings settings) {
    final PsiClass targetClass = settings.getTargetClass();

    if (targetClass == null) return null;

    String fieldName = settings.getName();
    String errorString = check(targetClass, fieldName, context);
    if (errorString != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(errorString);
      CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, getHelpID(), context.getProject());
      return null;
    }

    PsiField oldField = targetClass.findFieldByName(fieldName, true);
    if (oldField != null) {
      String message = RefactoringBundle.message("field.exists", fieldName, oldField.getContainingClass().getQualifiedName());
      int answer = Messages.showYesNoDialog(context.getProject(), message, getRefactoringName(), Messages.getWarningIcon());
      if (answer != 0) {
        return null;
      }
    }

    final GrVariableDeclaration declaration = createField(context, settings);
    if (targetClass.isInterface()) {
      declaration.getModifierList().setModifierProperty(PsiModifier.STATIC, false);
      declaration.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
    }

    final GrVariableDeclaration added;
    if (targetClass instanceof GrEnumTypeDefinition) {
      final GrEnumConstantList enumConstants = ((GrEnumTypeDefinition)targetClass).getEnumConstantList();
      added = (GrVariableDeclaration)targetClass.addAfter(declaration, enumConstants);
    } else {
      added = ((GrVariableDeclaration)targetClass.add(declaration));
    }
    GrReferenceAdjuster.shortenReferences(added);
    if (context.getVar() != null) {
      deleteLocalVar(context);
    }
    final GrField field = (GrField)added.getVariables()[0];
    if (settings.replaceAllOccurrences()) {
      GroovyRefactoringUtil.sortOccurrences(context.getOccurrences());
      for (PsiElement occurrence : context.getOccurrences()) {
        replaceOccurrence(field, occurrence, isEscalateVisibility(settings.getVisibilityModifier()));
      }
    }
    else {
      replaceOccurrence(field, context.getExpression(), isEscalateVisibility(settings.getVisibilityModifier()));
    }
    return (GrField)added.getVariables()[0];
  }

  @Nullable
  private static String check(PsiClass targetClass, final String fieldName, GrIntroduceContext context) {
    if (targetClass != null && !GroovyFileType.GROOVY_LANGUAGE.equals(targetClass.getLanguage())) {
      return GroovyRefactoringBundle.message("class.language.is.not.groovy");
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());

    if (fieldName == null || fieldName.isEmpty()) {
      return RefactoringBundle.message("no.field.name.specified");
    }

    else if (!facade.getNameHelper().isIdentifier(fieldName)) {
      return RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }

    if (targetClass instanceof GroovyScriptClass) {
      return GroovyRefactoringBundle.message("target.class.must.not.be.script");
    }

    return null;
  }


  private static void replaceOccurrence(GrField field, PsiElement occurrence, boolean escalateVisibility) {
    final PsiElement replaced;
    final GrReferenceExpression newExpr = createRefExpression(field, occurrence);
    if (occurrence instanceof GrExpression) {
      replaced = ((GrExpression)occurrence).replaceWithExpression(newExpr, false);
    }
    else {
      replaced = occurrence.replace(newExpr);
    }
    if (escalateVisibility) {
      PsiUtil.escalateVisibility(field, replaced);
    }
    if (replaced instanceof GrReferenceExpression) {
      GrReferenceAdjuster.shortenReference((GrReferenceExpression)replaced);
    }
  }

  private static GrReferenceExpression createRefExpression(GrField field, PsiElement place) {
    final PsiClass containingClass = field.getContainingClass();
    assert containingClass != null;
    final String refText = containingClass.getQualifiedName() + "." + field.getName();
    return GroovyPsiElementFactory.getInstance(place.getProject()).createReferenceExpressionFromText(refText, place);
  }

  private static GrVariableDeclaration createField(GrIntroduceContext context, GrIntroduceConstantSettings settings) {
    final String name = settings.getName();
    final PsiType type = settings.getSelectedType();
    final String modifier;
    if (isEscalateVisibility(settings.getVisibilityModifier())) {
      modifier = PsiModifier.PRIVATE;
    } else {
      modifier = settings.getVisibilityModifier();
    }
    String[] modifiers = modifier == null || PsiModifier.PACKAGE_LOCAL.equals(modifier)
                         ? new String[]{PsiModifier.STATIC, PsiModifier.FINAL}
                         : new String[]{modifier, PsiModifier.STATIC, PsiModifier.FINAL};
    return GroovyPsiElementFactory.getInstance(context.getProject()).createFieldDeclaration(modifiers, name, context.getExpression(), type);
  }

  private static boolean isEscalateVisibility(String modifier) {
    return VisibilityUtil.ESCALATE_VISIBILITY.equals(modifier);
  }

  @Nullable
  public static PsiClass getParentClass(PsiElement occurrence) {
    PsiElement cur = occurrence;
    while (true) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(cur, PsiClass.class, true);
      if (parentClass == null || parentClass.hasModifierProperty(PsiModifier.STATIC)) return parentClass;
      cur = parentClass;
    }
  }

  private static class ConstantChecker extends GroovyRecursiveElementVisitor {
    private final PsiElement scope;
    private final GrExpression expr;

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      final PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiVariable) {
        if (!isStaticFinalField((PsiVariable)resolved)) {
          if (expr instanceof GrClosableBlock) {
            if (!PsiTreeUtil.isContextAncestor(scope, resolved, true)) {
              throw new GrRefactoringError(GroovyRefactoringBundle.message("closure.uses.external.variables"));
            }
          }
          else {
            throw new GrRefactoringError(RefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
          }
        }
      }
      else if (resolved instanceof PsiMethod && ((PsiMethod)resolved).getContainingClass() != null) {
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null ||
            (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass)) {
          if (!((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            throw new GrRefactoringError(RefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
          }
        }
      }
    }

    private static boolean isStaticFinalField(PsiVariable var) {
      return var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty(PsiModifier.STATIC);
    }

    @Override
    public void visitClosure(GrClosableBlock closure) {
      if (closure == expr) {
        super.visitClosure(closure);
      }
      else {
        closure.accept(new ConstantChecker(closure, scope));
      }
    }

    private ConstantChecker(GrExpression expr, PsiElement expressionScope) {
      scope = expressionScope;
      this.expr = expr;
    }
  }
}
