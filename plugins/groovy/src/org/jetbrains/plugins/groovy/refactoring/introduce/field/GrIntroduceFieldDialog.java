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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.ui.Messages.getWarningIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;
import static com.intellij.refactoring.introduceField.IntroduceFieldHandler.REFACTORING_NAME;

public class GrIntroduceFieldDialog extends DialogWrapper implements GrIntroduceDialog<GrIntroduceFieldSettings>, GrIntroduceFieldSettings {
  private JPanel myContentPane;
  private TextFieldWithAutoCompletion<String> myNameField;
  private JRadioButton myPrivateRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPropertyRadioButton;
  private JRadioButton myCurrentMethodRadioButton;
  private JRadioButton myFieldDeclarationRadioButton;
  private JRadioButton myClassConstructorSRadioButton;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myReplaceAllOccurrencesCheckBox;
  private GrTypeComboBox myTypeComboBox;
  private JLabel myNameLabel;
  private JLabel myTypeLabel;
  private final boolean myIsStatic;
  private boolean isInvokedInAlwaysInvokedConstructor;
  private boolean hasLHSUsages;
  private String myInvokedOnLocalVar;
  private final boolean myCanBeInitializedOutsideBlock;

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private final GrIntroduceContext myContext;

  public GrIntroduceFieldDialog(GrIntroduceContext context) {
    super(context.getProject(), true);
    myContext = context;

    GrTypeDefinition clazz = (GrTypeDefinition)context.getScope();
    myIsStatic = GrIntroduceFieldHandler.shouldBeStatic(context.getPlace(), clazz);

    initVisibility();

    ButtonGroup initialization = new ButtonGroup();
    initialization.add(myCurrentMethodRadioButton);
    initialization.add(myFieldDeclarationRadioButton);
    initialization.add(myClassConstructorSRadioButton);
    new RadioUpDownListener(myCurrentMethodRadioButton, myFieldDeclarationRadioButton, myClassConstructorSRadioButton);

    myCanBeInitializedOutsideBlock = canBeInitializedOutsideBlock(context.getExpression(), (GrTypeDefinition)context.getScope());
    /*if (!myCanBeInitializedOutsideBlock) {
      myClassConstructorSRadioButton.setEnabled(false);
      myFieldDeclarationRadioButton.setEnabled(false);
    }*/
    final GrMethod containingMethod = GrIntroduceFieldHandler.getContainingMethod(context.getPlace(), clazz);
    if (containingMethod == null) {
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

    myNameField.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        validateOKAction();
      }
    });

    ItemListener l = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myNameField.requestFocusInWindow();
        checkErrors();
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

    isInvokedInAlwaysInvokedConstructor =
      allOccurrencesInOneMethod(myContext.getOccurrences(), clazz) && isAlwaysInvokedConstructor(containingMethod, clazz);
    hasLHSUsages = hasLhsUsages(myContext);

    setTitle(REFACTORING_NAME);
    init();
    checkErrors();
  }

  private void checkErrors() {
    List<String> errors = new ArrayList<String>();
    if (myCurrentMethodRadioButton.isSelected() && myDeclareFinalCheckBox.isSelected() && !!isInvokedInAlwaysInvokedConstructor) {
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
    if (errors.size() == 0) {
      setErrorText(null);
    }
    else {
      setErrorText(StringUtil.join(errors, "\n"));
    }
  }

  private static boolean hasLhsUsages(GrIntroduceContext context) {
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

    myPrivateRadioButton.setSelected(true);
    new RadioUpDownListener(myPrivateRadioButton, myProtectedRadioButton, myPublicRadioButton, myPropertyRadioButton);
  }

  private static boolean isAlwaysInvokedConstructor(PsiMethod method, GrTypeDefinition clazz) {
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

  private static boolean allOccurrencesInOneMethod(PsiElement[] occurrences, GrTypeDefinition clazz) {
    if (occurrences.length == 0) return true;
    GrMethod method = GrIntroduceFieldHandler.getContainingMethod(occurrences[0], clazz);
    if (method == null) return false;
    for (int i = 1; i < occurrences.length; i++) {
      GrMethod other = GrIntroduceFieldHandler.getContainingMethod(occurrences[i], clazz);
      if (other != method) return false;
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

  private void createUIComponents() {
    String[] possibleNames;
    final GroovyFieldValidator validator = new GroovyFieldValidator(myContext);
    final GrExpression expression = myContext.getExpression();
    final GrVariable var = myContext.getVar();
    if (expression != null) {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(expression, validator, true);
    }
    else {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNameByType(var.getType(), validator);
    }
    List<String> list = new ArrayList<String>();
    if (var != null) {
      list.add(var.getName());
    }
    ContainerUtil.addAll(list, possibleNames);
    myNameField = TextFieldWithAutoCompletion.create(myContext.getProject(), list, null, true);
    if (list.size()>0) {
      myNameField.setText(list.get(0));
      myNameField.selectAll();
    }

    if (expression == null) {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType()
      );
    }
    else {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(expression);
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
    return myNameField.getText().trim();
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
      if (GroovyRefactoringUtil.isLocalVariable(resolved)) {
        return ((GrVariable)resolved).getName();
      }
    }
    return null;
  }

  private static boolean canBeInitializedOutsideBlock(@Nullable GrExpression expression, final GrTypeDefinition scope) {
    if (expression == null) return false;
    expression = (GrExpression)PsiUtil.skipParentheses(expression, false);
    if (expression == null) return false;

    if (expression instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (GroovyRefactoringUtil.isLocalVariable(resolved)) {
        expression = ((GrVariable)resolved).getInitializerGroovy();
        if (expression == null) return false;
      }
    }

    final Ref<Boolean> ref = new Ref<Boolean>(Boolean.TRUE);
    final GrExpression finalExpression = expression;
    expression.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression refExpr) {
        super.visitReferenceExpression(refExpr);
        final PsiElement resolved = refExpr.resolve();
        if (!(resolved instanceof GrVariable)) return;
        if (resolved instanceof GrField && scope.getManager().areElementsEquivalent(scope, ((GrField)resolved).getContainingClass())) {
          return;
        }
        if (resolved instanceof PsiParameter &&
            PsiTreeUtil.isAncestor(finalExpression, ((PsiParameter)resolved).getDeclarationScope(), false)) {
          return;
        }
        ref.set(Boolean.FALSE);
      }
    });
    return ref.get();
  }

  private void validateOKAction() {
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(getName()));
  }

  @Override
  protected void doOKAction() {
    final GrTypeDefinition clazz = (GrTypeDefinition)myContext.getScope();
    final String name = getName();
    String message = RefactoringBundle.message("field.exists", name, clazz.getQualifiedName());
    if (clazz.findFieldByName(name, true) != null &&
        showYesNoDialog(myContext.getProject(), message, REFACTORING_NAME, getWarningIcon()) != 0) {
      return;
    }
    super.doOKAction();
  }
}
