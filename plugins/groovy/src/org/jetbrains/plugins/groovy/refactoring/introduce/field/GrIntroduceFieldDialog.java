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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
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
  private NameSuggestionsField myNameSuggestionsField;
  private JRadioButton myPrivateRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPropertyRadioButton;
  private JRadioButton myCurrentMethodRadioButton;
  private JRadioButton myFieldDeclarationRadioButton;
  private JRadioButton myClassConstructorSRadioButton;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myReplaceAllOccurencesCheckBox;
  private GrTypeComboBox myTypeComboBox;
  private JLabel myFieldLabel;
  private final boolean myIsStatic;
  private boolean isInvokedInAlwaysInvokedConstructor;
  private boolean hasLHSUsages;
  private String myInvokedOnLocalVar;
  private final boolean myCanBeInitializedOutsideBlock;

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  private final GrIntroduceContext myContext;

  public GrIntroduceFieldDialog(GrIntroduceContext context) {
    super(context.project, true);
    myContext = context;

    GrTypeDefinition clazz = (GrTypeDefinition)context.scope;
    myIsStatic = GrIntroduceFieldHandler.shouldBeStatic(context.expression, clazz);
    if (myIsStatic) {
      myFieldLabel.setText("Static Field of Type:");
    }
    else {
      myFieldLabel.setText("Field of Type:");
    }

    initVisibility();

    ButtonGroup initialization = new ButtonGroup();
    initialization.add(myCurrentMethodRadioButton);
    initialization.add(myFieldDeclarationRadioButton);
    initialization.add(myClassConstructorSRadioButton);
    new RadioUpDownListener(myCurrentMethodRadioButton, myFieldDeclarationRadioButton, myClassConstructorSRadioButton);

    myCanBeInitializedOutsideBlock = canBeInitializedOutsideBlock(context.expression, (GrTypeDefinition)context.scope);
    /*if (!myCanBeInitializedOutsideBlock) {
      myClassConstructorSRadioButton.setEnabled(false);
      myFieldDeclarationRadioButton.setEnabled(false);
    }*/
    if (GrIntroduceFieldHandler.getContainingMethod(context.expression, clazz) == null) {
      myCurrentMethodRadioButton.setEnabled(false);
    }

    if (myCurrentMethodRadioButton.isEnabled()) {
      myCurrentMethodRadioButton.setSelected(true);
    }
    else {
      myFieldDeclarationRadioButton.setSelected(true);
    }

    final String invokedOnLocalVar = getInvokedOnLocalVar(context.expression);
    if (invokedOnLocalVar != null) {
      myReplaceAllOccurencesCheckBox.setText("Replace all occurences and remove variable '" + invokedOnLocalVar + "'");
    }
    else if (context.occurrences.length == 1) {
      myReplaceAllOccurencesCheckBox.setSelected(false);
      myReplaceAllOccurencesCheckBox.setVisible(false);
    }

    myNameSuggestionsField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      @Override
      public void dataChanged() {
        validateOKAction();
      }
    });

    ItemListener l = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myNameSuggestionsField.requestFocusInWindow();
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
    myReplaceAllOccurencesCheckBox.addItemListener(l);
    myTypeComboBox.addItemListener(l);

    isInvokedInAlwaysInvokedConstructor = allOccurrencesInOneMethod(myContext.occurrences, (GrTypeDefinition)myContext.scope) &&
                                          isAlwaysInvokedConstructor(GrIntroduceFieldHandler.getContainingMethod(myContext.expression,
                                                                                                                 (GrTypeDefinition)myContext.scope),
                                                                     (GrTypeDefinition)myContext.scope);
    hasLHSUsages = hasLhsUsages(myContext);
    myInvokedOnLocalVar = getInvokedOnLocalVar(myContext.expression);
    init();
    checkErrors();
  }

  private void checkErrors() {
    List<String> errors = new ArrayList<String>();
    if (myCurrentMethodRadioButton.isSelected() && myDeclareFinalCheckBox.isSelected() && !!isInvokedInAlwaysInvokedConstructor) {
      errors.add(GroovyRefactoringBundle.message("final.field.cant.be.initialized.in.cur.method"));
    }
    if (myDeclareFinalCheckBox.isSelected() && myReplaceAllOccurencesCheckBox.isSelected() && myInvokedOnLocalVar != null && hasLHSUsages) {
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
    if (!(context.expression instanceof GrReferenceExpression)) return false;
    for (PsiElement element : context.occurrences) {
      if (element instanceof GrReferenceExpression) {
        if (PsiUtil.isLValue((GroovyPsiElement)element)) return true;
      }
    }
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
    final GrConstructorInvocation invocation = ((GrConstructor)method).getChainingConstructorInvocation();
    if (invocation != null && invocation.isThisCall()) return false;

    for (PsiMethod constructor : constructors) {
      if (constructor == method) continue;
      final GrConstructorInvocation inv = ((GrConstructor)constructor).getChainingConstructorInvocation();
      if (inv == null || inv.isSuperCall()) return false;
    }
    return true;
  }

  private static boolean allOccurrencesInOneMethod(PsiElement[] occurrences, GrTypeDefinition clazz) {
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
    return myContentPane;
  }

  @Override
  public GrIntroduceFieldSettings getSettings() {
    return this;
  }

  private void createUIComponents() {
    String[] possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(myContext.expression, new GroovyFieldValidator(myContext), true);
    myNameSuggestionsField = new NameSuggestionsField(possibleNames, myContext.project, GroovyFileType.GROOVY_FILE_TYPE);
    myTypeComboBox = new GrTypeComboBox(myContext.expression);
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
    if (myPrivateRadioButton.isSelected()) return GrModifier.PRIVATE;
    if (myProtectedRadioButton.isSelected()) return GrModifier.PROTECTED;
    if (myPublicRadioButton.isSelected()) return GrModifier.PUBLIC;
    if (myPropertyRadioButton.isSelected()) return GrModifier.PACKAGE_LOCAL;
    throw new IncorrectOperationException("no visibility selected");
  }

  @Override
  public boolean isStatic() {
    return myIsStatic;
  }

  @Override
  public boolean removeLocalVar() {
    return myInvokedOnLocalVar != null && myReplaceAllOccurencesCheckBox.isSelected();
  }

  @Override
  @NotNull
  public String getName() {
    return myNameSuggestionsField.getEnteredName();
  }

  @Override
  public boolean replaceAllOccurrences() {
    return myReplaceAllOccurencesCheckBox.isSelected();
  }

  @Override
  public PsiType getSelectedType() {
    return myTypeComboBox.getSelectedType();
  }

  @Nullable
  private static String getInvokedOnLocalVar(GrExpression expression) {
    if (expression instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (resolved instanceof GrVariable) {
        if (GroovyRefactoringUtil.isLocalVariable((GrVariable)resolved)) {
          return ((GrVariable)resolved).getName();
        }
      }
    }
    return null;
  }

  private static boolean canBeInitializedOutsideBlock(GrExpression expression, final GrTypeDefinition scope) {
    expression = (GrExpression)PsiUtil.skipParentheses(expression, false);
    if (expression == null) return false;

    if (expression instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (resolved instanceof GrVariable) {
        if (GroovyRefactoringUtil.isLocalVariable((GrVariable)resolved)) {
          expression = ((GrVariable)resolved).getInitializerGroovy();
          if (expression == null) return false;
        }
      }
    }

    final Ref<Boolean> ref = new Ref<Boolean>(Boolean.TRUE);
    expression.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression refExpr) {
        super.visitReferenceExpression(refExpr);
        final PsiElement resolved = refExpr.resolve();
        if (!(resolved instanceof GrVariable)) return;
        if (resolved instanceof GrField && scope.getManager().areElementsEquivalent(scope, ((GrField)resolved).getContainingClass())) {
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
    final GrTypeDefinition clazz = (GrTypeDefinition)myContext.scope;
    final String name = getName();
    if (clazz.findFieldByName(name, true) != null) {
      int answer =
        showYesNoDialog(myContext.project, RefactoringBundle.message("field.exists", name, clazz.getQualifiedName()), REFACTORING_NAME,
                        getWarningIcon());
      if (answer != 0) {
        return;
      }
    }
    super.doOKAction();
  }
}
