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

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;

public class GroovyIntroduceVariableDialog extends DialogWrapper implements GrIntroduceDialog<GroovyIntroduceVariableSettings> {
  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  private final Project myProject;
  private final GrExpression myExpression;
  private final int myOccurrencesCount;
  private final GrIntroduceVariableHandler.Validator myValidator;

  private final EventListenerList myListenerList;

  private JPanel contentPane;
  private ComboBox myNameComboBox;
  private JCheckBox myCbIsFinal;
  private JCheckBox myCbReplaceAllOccurrences;
  private GrTypeComboBox myTypeComboBox;
  private JLabel myTypeLabel;
  private JLabel myNameLabel;

  public GroovyIntroduceVariableDialog(GrIntroduceContext context,
                                       GrIntroduceVariableHandler.Validator validator,
                                       String[] possibleNames) {
    super(context.project, true);
    myProject = context.project;
    myExpression = context.expression;
    myOccurrencesCount = context.occurrences.length;
    myValidator = validator;

    myListenerList = new EventListenerList();
    setUpNameComboBox(possibleNames);

    setModal(true);
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
  protected String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String &&
        ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String) myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  protected boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurrences.isSelected();
  }

  private boolean isDeclareFinal() {
    return myCbIsFinal.isSelected();
  }

  @Nullable
  private PsiType getSelectedType() {
    return myTypeComboBox.getSelectedType();
  }

  private void setUpDialog() {

    myCbReplaceAllOccurrences.setMnemonic(KeyEvent.VK_A);
    myCbReplaceAllOccurrences.setFocusable(false);
    myCbIsFinal.setMnemonic(KeyEvent.VK_F);
    myCbIsFinal.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    myCbIsFinal.setSelected(GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS);

    // Replace occurrences
    if (myOccurrencesCount > 1) {
      myCbReplaceAllOccurrences.setSelected(false);
      myCbReplaceAllOccurrences.setEnabled(true);
      myCbReplaceAllOccurrences.setText(myCbReplaceAllOccurrences.getText() + " (" + myOccurrencesCount + " occurrences)");
    } else {
      myCbReplaceAllOccurrences.setSelected(false);
      myCbReplaceAllOccurrences.setEnabled(false);
    }

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

  }

  private void setUpNameComboBox(String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

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

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (!myValidator.isOK(this)) {
      return;
    }
    if (myCbIsFinal.isEnabled()) {
      GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = myCbIsFinal.isSelected();
    }
    super.doOKAction();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

  private void createUIComponents() {
    myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(myExpression);
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  public GroovyIntroduceVariableSettings getSettings() {
    return new MyGroovyIntroduceVariableSettings(this);
  }

  private static class MyGroovyIntroduceVariableSettings implements GroovyIntroduceVariableSettings {
    String myEnteredName;
    boolean myIsReplaceAllOccurrences;
    boolean myIsDeclareFinal;
    PsiType mySelectedType;

    public MyGroovyIntroduceVariableSettings(GroovyIntroduceVariableDialog dialog) {
      myEnteredName = dialog.getEnteredName();
      myIsReplaceAllOccurrences = dialog.isReplaceAllOccurrences();
      myIsDeclareFinal = dialog.isDeclareFinal();
      mySelectedType = dialog.getSelectedType();
    }

    public String getName() {
      return myEnteredName;
    }

    public boolean replaceAllOccurrences() {
      return myIsReplaceAllOccurrences;
    }

    public boolean isDeclareFinal() {
      return myIsDeclareFinal;
    }

    public PsiType getSelectedType() {
      return mySelectedType;
    }

  }
}
