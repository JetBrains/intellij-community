// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
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

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;

public class GrIntroduceFieldDialog extends DialogWrapper implements GrIntroduceDialog<GrIntroduceFieldSettings>, GrIntroduceFieldSettings {
  private final JPanel myContentPane;
  private final NameSuggestionsField myNameField;
  private final JRadioButton myPrivateRadioButton;
  private final JRadioButton myProtectedRadioButton;
  private final JRadioButton myPublicRadioButton;
  private final JRadioButton myPropertyRadioButton;
  private final JRadioButton myCurrentMethodRadioButton;
  private final JRadioButton myFieldDeclarationRadioButton;
  private final JRadioButton myClassConstructorSRadioButton;
  private final JBRadioButton mySetUpMethodRadioButton;
  private final JCheckBox myDeclareFinalCheckBox;
  private final JCheckBox myReplaceAllOccurrencesCheckBox;
  private final GrTypeComboBox myTypeComboBox;
  private final JLabel myNameLabel;
  private final JLabel myTypeLabel;
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
    {
      final GrExpression expression = myContext.getExpression();
      final GrVariable var = myContext.getVar();
      final StringPartInfo stringPart = myContext.getStringPart();

      List<String> list = new ArrayList<>();
      if (var != null) {
        list.add(var.getName());
      }
      list.addAll(suggestNames());
      myNameField = new NameSuggestionsField(ArrayUtilRt.toStringArray(list), myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE);

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
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myContentPane = new JPanel();
      myContentPane.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      myContentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      myTypeLabel = new JLabel();
      this.$$$loadLabelText$$$(myTypeLabel, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "type.label"));
      panel1.add(myTypeLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myNameLabel, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "name.label"));
      panel1.add(myNameLabel,
                 new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel1.add(myNameField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel1.add(myTypeComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myContentPane.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel3.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
      panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(null, this.$$$getMessageFromBundle$$$(
                                                                                  "messages/GroovyRefactoringBundle", "initialize.in.border.title"), TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                TitledBorder.DEFAULT_POSITION, null, null));
      myCurrentMethodRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myCurrentMethodRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "initialize.in.current.method.choice"));
      panel3.add(myCurrentMethodRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      myFieldDeclarationRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myFieldDeclarationRadioButton, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle",
                                                                                               "initialize.in.field.declaration.choice"));
      panel3.add(myFieldDeclarationRadioButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
      myClassConstructorSRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myClassConstructorSRadioButton, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle",
                                                                                                "initialize.in.class.constructor.choice"));
      panel3.add(myClassConstructorSRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
      mySetUpMethodRadioButton = new JBRadioButton();
      mySetUpMethodRadioButton.setHorizontalAlignment(10);
      this.$$$loadButtonText$$$(mySetUpMethodRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "initialize.in.setup.method.choice"));
      panel3.add(mySetUpMethodRadioButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel4.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
      panel2.add(panel4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      panel4.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(null, this.$$$getMessageFromBundle$$$(
                                                                                  "messages/GroovyRefactoringBundle", "visibility.border.title"), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                                                                                null, null));
      myPrivateRadioButton = new JRadioButton();
      myPrivateRadioButton.setSelected(false);
      this.$$$loadButtonText$$$(myPrivateRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "visibility.private.choice"));
      panel4.add(myPrivateRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myProtectedRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myProtectedRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "visibility.protected.choice"));
      panel4.add(myProtectedRadioButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPublicRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myPublicRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "visibility.public.choice"));
      panel4.add(myPublicRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPropertyRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myPropertyRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "visibility.property.choice"));
      panel4.add(myPropertyRadioButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myDeclareFinalCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myDeclareFinalCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "declare.final.checkbox"));
      myContentPane.add(myDeclareFinalCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
      myReplaceAllOccurrencesCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myReplaceAllOccurrencesCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "replace.all.occurrences.checkbox"));
      myContentPane.add(myReplaceAllOccurrencesCheckBox,
                        new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      myContentPane.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    final PsiClass clazz = (PsiClass)context.getScope();
    PsiElement scope = clazz instanceof GroovyScriptClass ? clazz.getContainingFile() : clazz;
    myIsStatic = GrIntroduceFieldHandler.shouldBeStatic(context.getPlace(), scope);

    initVisibility();

    ButtonGroup initialization = new ButtonGroup();
    ArrayList<JRadioButton> inits = new ArrayList<>();

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
    RadioUpDownListener.installOn(inits.toArray(new JRadioButton[0]));

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

    myInvokedOnLocalVar = context.getVar() == null ? getInvokedOnLocalVar(context.getExpression()) : context.getVar().getName();
    if (myInvokedOnLocalVar != null) {
      myReplaceAllOccurrencesCheckBox.setText(GroovyBundle.message("replace.all.occurrences.and.remove.variable.0", myInvokedOnLocalVar));
      if (context.getVar() != null) {
        myReplaceAllOccurrencesCheckBox.setEnabled(false);
        myReplaceAllOccurrencesCheckBox.setSelected(true);
      }
    }
    else if (context.getOccurrences().length == 1) {
      myReplaceAllOccurrencesCheckBox.setSelected(false);
      myReplaceAllOccurrencesCheckBox.setVisible(false);
    }

    myNameField.addDataChangedListener(() -> validateOKAction());

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

    setTitle(IntroduceFieldHandler.getRefactoringNameText());
    init();
    checkErrors();
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myContentPane; }

  private void checkErrors() {
    List<String> errors = new ArrayList<>();
    if (myCurrentMethodRadioButton.isSelected() && myDeclareFinalCheckBox.isSelected() && !isInvokedInAlwaysInvokedConstructor) {
      errors.add(GroovyRefactoringBundle.message("final.field.cant.be.initialized.in.cur.method"));
    }
    if (myDeclareFinalCheckBox.isSelected() &&
        myReplaceAllOccurrencesCheckBox.isSelected() &&
        myInvokedOnLocalVar != null &&
        hasLHSUsages) {
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
      setErrorText(errorString(errors));
    }
  }

  private static @NlsSafe @NotNull String errorString(List<String> errors) {
    return StringUtil.join(errors, "\n");
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
    RadioUpDownListener.installOn(myPrivateRadioButton, myProtectedRadioButton, myPublicRadioButton, myPropertyRadioButton);
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

  private static boolean allOccurrencesInOneMethod(PsiElement @NotNull [] occurrences, PsiElement scope) {
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

  @Override
  public @NotNull LinkedHashSet<String> suggestNames() {
    return new GrFieldNameSuggester(myContext, new GroovyFieldValidator(myContext), false).suggestNames();
  }

  @Override
  public boolean declareFinal() {
    return myDeclareFinalCheckBox.isSelected();
  }

  @Override
  public @NotNull Init initializeIn() {
    if (myCurrentMethodRadioButton.isSelected()) return Init.CUR_METHOD;
    if (myFieldDeclarationRadioButton.isSelected()) return Init.FIELD_DECLARATION;
    if (myClassConstructorSRadioButton.isSelected()) return Init.CONSTRUCTOR;
    if (mySetUpMethodRadioButton.isSelected()) return Init.SETUP_METHOD;
    throw new IncorrectOperationException("no initialization place is selected");
  }

  @Override
  public @NotNull String getVisibilityModifier() {
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
  public @NotNull String getName() {
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

  private static @Nullable String getInvokedOnLocalVar(GrExpression expression) {
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
          return false;
        }
      }
      return true;
    }

    else {
      return false;
    }
  }

  private static final class ExpressionChecker extends GroovyRecursiveElementVisitor {
    private final PsiClass myClass;
    private final PsiElement myScope;

    private boolean result = true;

    private ExpressionChecker(@NotNull PsiClass aClass, @NotNull PsiElement scope) {
      myClass = aClass;
      myScope = scope;
    }

    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression refExpr) {
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
        Messages.showYesNoDialog(myContext.getProject(), message, IntroduceFieldHandler.getRefactoringNameText(),
                                 Messages.getWarningIcon()) != Messages.YES) {
      return;
    }
    super.doOKAction();
  }
}
