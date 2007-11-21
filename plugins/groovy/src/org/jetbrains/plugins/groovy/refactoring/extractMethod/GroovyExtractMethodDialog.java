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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.EditorTextField;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringSettings;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.EventListener;

/**
 * @author ilyas
 */
public class GroovyExtractMethodDialog extends DialogWrapper implements ExtractMethodSettings {

  private final ExtractMethodInfoHelper myHelper;
  private final Project myProject;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");

  private JPanel contentPane;
  private EditorTextField myNameField;
  private JCheckBox myCbSpecifyType;
  private JLabel myNameLabel;
  private JTextArea mySignatureArea;
  private VisibilityPanel myVisibilityPanel;
  private JButton buttonOK;

  public GroovyExtractMethodDialog(ExtractMethodInfoHelper helper, Project project) {
    super(project, true);
    myProject = project;
    myHelper = helper;
    setUpNameField();

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    update();
  }

  protected void doOKAction() {
    if (myCbSpecifyType.isEnabled()) {
      GroovyRefactoringSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE = myCbSpecifyType.isSelected();
    }
    GroovyRefactoringSettings.getInstance().EXTRACT_METHOD_VISIBILITY = myVisibilityPanel.getVisibility();
    super.doOKAction();
  }

  private void setUpDialog() {
    myCbSpecifyType.setMnemonic(KeyEvent.VK_T);
    myCbSpecifyType.setEnabled(myHelper.specifyType());
    myCbSpecifyType.setSelected(myHelper.specifyType());
    if (myCbSpecifyType.isEnabled() && GroovyRefactoringSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE != null) {
      myCbSpecifyType.setSelected(GroovyRefactoringSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE);
    }

    myCbSpecifyType.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myHelper.setSpecifyType(myCbSpecifyType.isSelected());
        updateSignature();
      }
    });

    myHelper.setSpecifyType(myCbSpecifyType.isSelected());
    myHelper.setVisibility(myVisibilityPanel.getVisibility());
    myNameLabel.setLabelFor(myNameField);
  }

  private void setUpNameField() {

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameField.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    myNameField.addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    myListenerList.add(DataChangedListener.class, new DataChangedListener());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  @NotNull
  public ExtractMethodInfoHelper getHelper() {
    return myHelper;
  }

  private void update() {
    String text = getEnteredName();
    updateSignature();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

  public String getEnteredName() {
    String text = myNameField.getText();
    if (text != null && text.trim().length() > 0) {
      return text.trim();
    } else {
      return null;
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_METHOD);
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  private void createUIComponents() {
    myNameField = new EditorTextField("", myProject, GroovyFileType.GROOVY_FILE_TYPE);
    myVisibilityPanel = new VisibilityPanel();

    String visibility = GroovyRefactoringSettings.getInstance().EXTRACT_METHOD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    myVisibilityPanel.setVisibility(visibility);
    myVisibilityPanel.addStateChangedListener(new VisibilityPanel.VisibilityStateChanged() {
      public void visibilityChanged(String newVisibility) {
        myHelper.setVisibility(newVisibility);
        updateSignature();
      }
    });
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      update();
    }
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  /*
  Update signature text area
   */
  private void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(ExtractMethodUtil.getModifierString(myHelper));
    buffer.append(ExtractMethodUtil.getTypeString(myHelper));
    String name = getEnteredName() == null ? "" : getEnteredName();
    buffer.append(name);
    buffer.append("(\n");
    String[] params = ExtractMethodUtil.getParameterString(myHelper);
    for (String param : params) {
      buffer.append("  ").append(param).append("\n");
    }
    buffer.append(")");
    mySignatureArea.setText(buffer.toString());
  }


}
