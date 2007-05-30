/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.HelpID;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.refactoring.introduceVariable.GroovyIntroduceVariableSettings;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.HashMap;
import java.awt.event.KeyEvent;

public class GroovyIntroduceVariableDialog extends DialogWrapper implements GroovyIntroduceVariableSettings {

  private Project myProject;
  private final PsiElement myExpression;
  private final PsiType myType;
  private final int myOccurrencesCount;
  private final boolean myDeclareFinalIfAll;
  private final IntroduceVariableHandler.Validator myValidator;
  private HashMap<String, PsiType> myTypeMap = null;

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  private JPanel contentPane;
  private JComboBox myNameSelector;
  private JCheckBox myCbIsFinal;
  private JCheckBox myCbReplaceAllOccurences;
  private JCheckBox myCbTypeSpec;
  private JComboBox myTypeSelector;
  private JLabel myTypeLabel;
  private JButton buttonOK;

  public GroovyIntroduceVariableDialog(Project project,
                                       PsiElement expression,
                                       PsiType psiType,
                                       int occurrencesCount,
                                       boolean declareFinalIfAll,
                                       IntroduceVariableHandler.Validator validator) {
    super(project, true);
    myProject = project;
    myExpression = expression;
    myType = psiType;
    myOccurrencesCount = occurrencesCount;
    myDeclareFinalIfAll = declareFinalIfAll;
    myValidator = validator;

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  @Nullable
  public String getEnteredName() {
    // todo add validator!
    if (myNameSelector.getSelectedItem() instanceof String) {
      return (String) myNameSelector.getSelectedItem();
    } else {
      return "preved";
    }
  }

  public boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurences.isSelected();
  }

  public boolean isDeclareFinal() {
    return myCbIsFinal.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeMap.get(myTypeSelector.getSelectedItem());
  }

  private void setUpDialog() {

    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbIsFinal.setMnemonic(KeyEvent.VK_F);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);

    // Type specification
    if (myType == null) {
      myCbTypeSpec.setSelected(false);
      myCbTypeSpec.setEnabled(false);
      myTypeSelector.setEnabled(false);
    } else {
      myCbTypeSpec.setSelected(true);
      myTypeMap = GroovyRefactoringUtil.getCompatibleTypeNames(myType);
      for (String typeName : myTypeMap.keySet()) {
        myTypeSelector.addItem(typeName);
      }
    }

    // Replace occurences
    if (myOccurrencesCount > 1) {
      myCbReplaceAllOccurences.setSelected(true);
      myCbReplaceAllOccurences.setEnabled(true);
      myCbReplaceAllOccurences.setText(myCbReplaceAllOccurences.getText() + " (" + myOccurrencesCount + " occurences)");
    } else {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(false);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameSelector;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

}
