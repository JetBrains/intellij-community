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

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.KeyEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.EventListener;
import java.util.HashMap;

public class GroovyIntroduceVariableDialog extends DialogWrapper implements GroovyIntroduceVariableSettings {

  private Project myProject;
  private final PsiElement myExpression;
  private final PsiType myType;
  private final int myOccurrencesCount;
  private final boolean myDeclareFinalIfAll;
  private final IntroduceVariableHandler.Validator myValidator;
  private HashMap<String, PsiType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  private JPanel contentPane;
  private ComboBox myNameComboBox;
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
    setUpNameComboBox();

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    updateOkStatus();
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
    if (myNameComboBox.getEditor().getItem() instanceof String &&
        ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String) myNameComboBox.getEditor().getItem();
    } else {
      return null;
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
    myNameComboBox.setFocusCycleRoot(true);
    myNameComboBox.setFocusTraversalPolicyProvider(true);

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

  private void setUpNameComboBox() {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    // todo remove me!
    comboEditor.setItem("preved");

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);

    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireNameDataChanged();
          }
        }
    );

    ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    }
    );
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
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

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(PsiManager.getInstance(myProject).getNameHelper().isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

}
