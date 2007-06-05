/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.refactoring.introduceVariable.GroovyIntroduceVariableSettings;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.HashMap;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

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
    if (myNameSelector.getSelectedItem() instanceof String &&
        ((String) myNameSelector.getSelectedItem()).length() > 0) {
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
    if (!myCbTypeSpec.isSelected()) {
      return null;
    } else {
      return myTypeMap.get(myTypeSelector.getSelectedItem());
    }
  }

  private void setUpDialog() {

    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbIsFinal.setMnemonic(KeyEvent.VK_F);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myNameSelector.setFocusCycleRoot(true);
    myNameSelector.setFocusTraversalPolicyProvider(true);

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
      myCbReplaceAllOccurences.setSelected(false);
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

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    // todo implement validator!
    super.doOKAction();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }


}
