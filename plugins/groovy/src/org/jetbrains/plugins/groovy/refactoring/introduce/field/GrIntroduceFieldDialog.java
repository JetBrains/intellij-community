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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class GrIntroduceFieldDialog extends DialogWrapper implements GrIntroduceDialog<GrIntroduceFieldSettings>, GrIntroduceFieldSettings {
  private JPanel myContentPane;
  private NameSuggestionsField myNameField;
  private JRadioButton myPrivateRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPropertyRadioButton;
  private JRadioButton myCurrentMethodRadioButton;
  private JRadioButton myFieldDeclarationRadioButton;
  private JRadioButton myClassConstructorSRadioButton;
  private JBRadioButton mySetUpMethodRadioButton;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myReplaceAllOccurrencesCheckBox;
  private GrTypeComboBox myTypeComboBox;
  private JLabel myNameLabel;
  private JLabel myTypeLabel;
  private final boolean myIsStatic;
  private final boolean isInvokedInAlwaysInvokedConstructor;
  private final boolean hasLHSUsages;
  private final String myInvokedOnLocalVar;
  private final boolean myCanBeInitializedOutsideBlock;

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private final GrIntroduceContext myContext;

  public GrIntroduceFieldDialog(final GrIntroduceContext context) {
    super(context.getProject(), true);
    myContext = context;

    final PsiClass clazz = (PsiClass)context.getScope();
    PsiElement scope = clazz instanceof GroovyScriptClass ? clazz.getContainingFile() : clazz;
    myIsStatic = GrIntroduceFieldHandler.shouldBeStatic(context.getPlace(), scope);

    initVisibility();

    ButtonGroup initialization = new ButtonGroup();
    ArrayList<JRadioButton> inits = ContainerUtil.newArrayList();

    inits.add(myCurrentMethodRadioButton);
    inits.add(myFieldDeclarationRadioButton);
    inits.add(myClassConstructorSRadioButton);

    if (TestFrameworks.getInstance().isTestClass(clazz)) {
      inits.add(mySetUpMethodRadioButton);
    }
    else {
      mySetUpMethodRadioButton.setVisible(false);
    }

    for (JRadioButton init : inits) {
      initialization.add(init);
    }
    new RadioUpDownListener(inits.toArray(new JRadioButton[inits.size()]));

    if (clazz instanceof GroovyScriptClass) {
      myClassConstructorSRadioButton.setEnabled(false);
    }

    myCanBeInitializedOutsideBlock = canBeInitializedOutsideBlock(context, clazz);
    final GrMember container = GrIntroduceFieldHandler.getContainer(context.getPlace(), scope);
    if (container == null) {
      myCurrentMethodRadioButton.setEnabled(false);
    }

    if (myCurrentMethodRadioButton.isEnabled()) {
      myCurrentMethodRadioButton.setSelected(true);
    }
    else {
      myFieldDeclarationRadioButton.setSelected(true);
    }

    myInvokedOnLocalVar  = context.getVar() == null ? getInvokedOnLocalVar(context.getExpression()) : context.getVar().getName();
    if (myInvokedOnLocalVar != null) {
      myReplaceAllOccurrencesCheckBox.setText("Replace all occurrences and remove variable '" + myInvokedOnLocalVar + "'");
      if (context.getVar() != null) {
        myReplaceAllOccurrencesCheckBox.setEnabled(false);
        myReplaceAllOccurrencesCheckBox.setSelected(true);
      }
    }
    else if (context.getOccurrences().length == 1) {
      myReplaceAllOccurrencesCheckBox.setSelected(false);
      myReplaceAllOccurrencesCheckBox.setVisible(false);
    }

    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      @Override
      public void dataChanged() {
        validateOKAction();
      }
    });

    ItemListener l = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myNameField.requestFocusInWindow();
        checkErrors();

        if (myReplaceAllOccurrencesCheckBox.isSelected()) {
          PsiElement anchor = GrIntroduceHandlerBase.findAnchor(myContext.getOccurrences(), myContext.getScope());
          if (anchor != null && anchor != myContext.getScope() && anchor != ((GrTypeDefinition)myContext.getScope()).getBody()) {
            myCurrentMethodRadioButton.setEnabled(true);
          }
          else if (myCurrentMethodRadioButton.isEnabled()) {
            myCurrentMethodRadioButton.setEnabled(false);
            myFieldDeclarationRadioButton.setSelected(true);
          }
        }
        else if (!myCurrentMethodRadioButton.isEnabled()) {
          myCurrentMethodRadioButton.setEnabled(true);
        }
      }
    };
    myPrivateRadioButton.addItemListener(l);
    myProtectedRadioButton.addItemListener(l);
    myPublicRadioButton.addItemListener(l);
    myPropertyRadioButton.addItemListener(l);
    myCurrentMethodRadioButton.addItemListener(l);
    myFieldDeclarationRadioButton.addItemListener(l);
    myClassConstructorSRadioButton.addItemListener(l);
    myDeclareFinalCheckBox.addItemListener(l);
    myReplaceAllOccurrencesCheckBox.addItemListener(l);
    myTypeComboBox.addItemListener(l);

    isInvokedInAlwaysInvokedConstructor = container instanceof PsiMethod &&
                                          allOccurrencesInOneMethod(myContext.getOccurrences(), scope) &&
                                          isAlwaysInvokedConstructor((PsiMethod)container, clazz);
    hasLHSUsages = hasLhsUsages(myContext);

    setTitle(IntroduceFieldHandler.REFACTORING_NAME);
    init();
    checkErrors();
  }

  private void checkErrors() {
    List<String> errors = new ArrayList<>();
    if (myCurrentMethodRadioButton.isSelected() && myDeclareFinalCheckBox.isSelected() && !isInvokedInAlwaysInvokedConstructor) {
      errors.add(GroovyRefactoringBundle.message("final.field.cant.be.initialized.in.cur.method"));
    }
    if (myDeclareFinalCheckBox.isSelected() && myReplaceAllOccurrencesCheckBox.isSelected() && myInvokedOnLocalVar != null && hasLHSUsages) {
      errors.add(GroovyRefactoringBundle.message("Field.cannot.be.final.because.replaced.variable.has.lhs.usages"));
    }
    if (!myCanBeInitializedOutsideBlock) {
      if (myFieldDeclarationRadioButton.isSelected()) {
        errors.add(GroovyRefactoringBundle.message("field.cannot.be.initialized.in.field.declaration"));
      }
      else if (myClassConstructorSRadioButton.isSelected()) {
        errors.add(GroovyRefactoringBundle.message("field.cannot.be.initialized.in.constructor(s)"));
      }
    }
    if (errors.isEmpty()) {
      setErrorText(null);
    }
    else {
      setErrorText(StringUtil.join(errors, "\n"));
    }
  }

  private static boolean hasLhsUsages(@NotNull GrIntroduceContext context) {
    if (context.getVar() == null && !(context.getExpression() instanceof GrReferenceExpression)) return false;
    if (GrIntroduceHandlerBase.hasLhs(context.getOccurrences())) return true;
    return false;
  }

  private void initVisibility() {
    ButtonGroup visibility = new ButtonGroup();
    visibility.add(myPrivateRadioButton);
    visibility.add(myProtectedRadioButton);
    visibility.add(myPublicRadioButton);
    visibility.add(myPropertyRadioButton);

    if (myContext.getScope() instanceof GroovyScriptClass) {
      myPropertyRadioButton.setSelected(true);
      myPrivateRadioButton.setEnabled(false);
      myProtectedRadioButton.setEnabled(false);
      myPublicRadioButton.setEnabled(false);
      myPropertyRadioButton.setEnabled(false);
    }
    else {
      myPrivateRadioButton.setSelected(true);
    }
    new RadioUpDownListener(myPrivateRadioButton, myProtectedRadioButton, myPublicRadioButton, myPropertyRadioButton);
  }

  private static boolean isAlwaysInvokedConstructor(@Nullable PsiMethod method, @NotNull PsiClass clazz) {
    if (method == null) return false;
    if (!method.isConstructor()) return false;
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 1) return true;
    final GrConstructorInvocation invocation = PsiImplUtil.getChainingConstructorInvocation((GrMethod)method);
    if (invocation != null && invocation.isThisCall()) return false;

    for (PsiMethod constructor : constructors) {
      if (constructor == method) continue;
      final GrConstructorInvocation inv = PsiImplUtil.getChainingConstructorInvocation((GrMethod)constructor);
      if (inv == null || inv.isSuperCall()) return false;
    }
    return true;
  }

  private static boolean allOccurrencesInOneMethod(@NotNull PsiElement[] occurrences, PsiElement scope) {
    if (occurrences.length == 0) return true;
    GrMember container = GrIntroduceFieldHandler.getContainer(occurrences[0], scope);
    if (container == null) return false;
    for (int i = 1; i < occurrences.length; i++) {
      GrMember other = GrIntroduceFieldHandler.getContainer(occurrences[i], scope);
      if (other != container) return false;
    }
    return true;
  }

  @Override
  protected JComponent createCenterPanel() {
    myNameLabel.setLabelFor(myNameField);
    myTypeLabel.setLabelFor(myTypeComboBox);
    return myContentPane;
  }

  @Override
  public GrIntroduceFieldSettings getSettings() {
    return this;
  }

  @NotNull
  @Override
  public LinkedHashSet<String> suggestNames() {
    return new GrFieldNameSuggester(myContext, new GroovyFieldValidator(myContext), false).suggestNames();
  }

  private void createUIComponents() {
    final GrExpression expression = myContext.getExpression();
    final GrVariable var = myContext.getVar();
    final StringPartInfo stringPart = myContext.getStringPart();

    List<String> list = new ArrayList<>();
    if (var != null) {
      list.add(var.getName());
    }
    ContainerUtil.addAll(list, suggestNames());
    myNameField = new NameSuggestionsField(ArrayUtil.toStringArray(list), myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE);

    if (expression != null) {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(expression);
    }
    else if (stringPart != null) {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(stringPart.getLiteral());
    }
    else {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType(), var);
    }

    GrTypeComboBox.registerUpDownHint(myNameField, myTypeComboBox);
  }

  @Override
  public boolean declareFinal() {
    return myDeclareFinalCheckBox.isSelected();
  }

  @Override
  @NotNull
  public Init initializeIn() {
    if (myCurrentMethodRadioButton.isSelected()) return Init.CUR_METHOD;
    if (myFieldDeclarationRadioButton.isSelected()) return Init.FIELD_DECLARATION;
    if (myClassConstructorSRadioButton.isSelected()) return Init.CONSTRUCTOR;
    if (mySetUpMethodRadioButton.isSelected()) return Init.SETUP_METHOD;
    throw new IncorrectOperationException("no initialization place is selected");
  }

  @Override
  @NotNull
  public String getVisibilityModifier() {
    if (myPrivateRadioButton.isSelected()) return PsiModifier.PRIVATE;
    if (myProtectedRadioButton.isSelected()) return PsiModifier.PROTECTED;
    if (myPublicRadioButton.isSelected()) return PsiModifier.PUBLIC;
    if (myPropertyRadioButton.isSelected()) return PsiModifier.PACKAGE_LOCAL;
    throw new IncorrectOperationException("no visibility selected");
  }

  @Override
  public boolean isStatic() {
    return myIsStatic;
  }

  @Override
  public boolean removeLocalVar() {
    return myInvokedOnLocalVar != null && myReplaceAllOccurrencesCheckBox.isSelected();
  }

  @Override
  @NotNull
  public String getName() {
    return myNameField.getEnteredName();
  }

  @Override
  public boolean replaceAllOccurrences() {
    return myReplaceAllOccurrencesCheckBox.isSelected();
  }

  @Override
  public PsiType getSelectedType() {
    return myTypeComboBox.getSelectedType();
  }

  @Nullable
  private static String getInvokedOnLocalVar(GrExpression expression) {
    if (expression instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (PsiUtil.isLocalVariable(resolved)) {
        return ((GrVariable)resolved).getName();
      }
    }
    return null;
  }

  private static boolean canBeInitializedOutsideBlock(@NotNull GrIntroduceContext context, @NotNull PsiClass clazz) {
    final StringPartInfo part = context.getStringPart();
    GrExpression expression = context.getExpression();

    if (expression != null) {
      expression = (GrExpression)PsiUtil.skipParentheses(expression, false);
      if (expression == null) return false;

      if (expression instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
        if (PsiUtil.isLocalVariable(resolved)) {
          expression = ((GrVariable)resolved).getInitializerGroovy();
          if (expression == null) return false;
        }
      }

      ExpressionChecker visitor = new ExpressionChecker(clazz, expression);
      expression.accept(visitor);
      return visitor.isResult();
    }

    if (part != null) {
      for (GrStringInjection injection : part.getInjections()) {
        GroovyPsiElement scope = injection.getExpression() != null ? injection.getExpression() : injection.getClosableBlock();
        assert scope != null;
        ExpressionChecker visitor = new ExpressionChecker(clazz, scope);
        scope.accept(visitor);
        if (!visitor.isResult()) {
          return visitor.isResult();
        }
      }
      return true;
    }

    else {
      return false;
    }
  }

  private static class ExpressionChecker extends GroovyRecursiveElementVisitor {
    private final PsiClass myClass;
    private final PsiElement myScope;

    private boolean result = true;

    private ExpressionChecker(@NotNull PsiClass aClass, @NotNull PsiElement scope) {
      myClass = aClass;
      myScope = scope;
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression refExpr) {
      super.visitReferenceExpression(refExpr);
      final PsiElement resolved = refExpr.resolve();
      if (!(resolved instanceof GrVariable)) return;
      if (resolved instanceof GrField && myClass.getManager().areElementsEquivalent(myClass, ((GrField)resolved).getContainingClass())) {
        return;
      }
      if (resolved instanceof PsiParameter &&
          PsiTreeUtil.isAncestor(myScope, ((PsiParameter)resolved).getDeclarationScope(), false)) {
        return;
      }
      result = false;
    }

    private boolean isResult() {
      return result;
    }
  }

  private void validateOKAction() {
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(getName()));
  }

  @Override
  protected void doOKAction() {
    final PsiClass clazz = (PsiClass)myContext.getScope();
    final String name = getName();
    String message = RefactoringBundle.message("field.exists", name, clazz.getQualifiedName());
    if (clazz.findFieldByName(name, true) != null &&
        Messages.showYesNoDialog(myContext.getProject(), message, IntroduceFieldHandler.REFACTORING_NAME, Messages.getWarningIcon()) != Messages.YES) {
      return;
    }
    super.doOKAction();
  }
}
