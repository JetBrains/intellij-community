/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInitialInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterTablePanel;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EventListener;

/**
 * @author ilyas
 */
public class GroovyExtractMethodDialog extends DialogWrapper {

  private final ExtractInitialInfo myInfo;
  private final ExtractMethodInfoHelper myHelper;

  private final EventListenerList myListenerList = new EventListenerList();

  private JPanel contentPane;
  private EditorTextField myNameField;
  private JCheckBox myCbSpecifyType;
  private JLabel myNameLabel;
  private JTextArea mySignatureArea;
  private VisibilityPanel myVisibilityPanel;
  private ParameterTablePanel myParameterTablePanel;
  private final Project myProject;

  public GroovyExtractMethodDialog(ExtractInitialInfo info) {
    super(info.getProject(), true);
    myProject = info.getProject();
    myInfo = info;
    myHelper = new ExtractMethodInfoHelper(info, "");

    setUpNameField();
    myParameterTablePanel.init(this, myInfo);

    setModal(true);
    setTitle(GroovyExtractMethodHandler.REFACTORING_NAME);
    init();
    setUpDialog();
    update();
  }

  protected void doOKAction() {
    String name = getEnteredName();
    if (name == null) return;
    GrMethod method = ExtractUtil.createMethodByHelper(name, myHelper);
    if (method != null && !ExtractUtil.validateMethod(method, myHelper)) {
      return;
    }
    if (myCbSpecifyType.isEnabled()) {
      GroovyApplicationSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE = myCbSpecifyType.isSelected();
    }
    GroovyApplicationSettings.getInstance().EXTRACT_METHOD_VISIBILITY = myVisibilityPanel.getVisibility();
    super.doOKAction();
  }

  private void setUpDialog() {
    myCbSpecifyType.setMnemonic(KeyEvent.VK_T);
    myCbSpecifyType.setFocusable(false);
    myCbSpecifyType.setSelected(true);
    if (GroovyApplicationSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE != null) {
      myCbSpecifyType.setSelected(GroovyApplicationSettings.getInstance().EXTRACT_METHOD_SPECIFY_TYPE);
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
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

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
  protected ExtractMethodInfoHelper getHelper() {
    return myHelper;
  }

  private void update() {
    String text = getEnteredName();
    myHelper.setName(text);
    updateSignature();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

  @Nullable
  protected String getEnteredName() {
    String text = myNameField.getText();
    if (text != null && text.trim().length() > 0) {
      return text.trim();
    }
    else {
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

    String visibility = GroovyApplicationSettings.getInstance().EXTRACT_METHOD_VISIBILITY;
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

    myParameterTablePanel = new ParameterTablePanel();
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
        ((DataChangedListener)aList).dataChanged();
      }
    }
  }

  /*
  Update signature text area
   */
  public void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuilder buffer = new StringBuilder();
    String modifier = ExtractUtil.getModifierString(myHelper);
    buffer.append(modifier);
    buffer.append(ExtractUtil.getTypeString(myHelper, true, modifier));
    String name = getEnteredName() == null ? "" : getEnteredName();
    buffer.append(name);
    buffer.append("(");
    String[] params = ExtractUtil.getParameterString(myHelper);
    if (params.length > 0) {
      String INDENT = "    ";
      buffer.append("\n");
      for (String param : params) {
        buffer.append(INDENT).append(param).append("\n");
      }
    }
    buffer.append(")");
    mySignatureArea.setText(buffer.toString());
  }

  public ExtractMethodSettings getSettings() {
    return new MyExtractMethodSettings(this);
  }

  private static class MyExtractMethodSettings implements ExtractMethodSettings {
    ExtractMethodInfoHelper myHelper;
    String myEnteredName;

    public MyExtractMethodSettings(GroovyExtractMethodDialog dialog) {
      myHelper = dialog.getHelper();
      myEnteredName = dialog.getEnteredName();
    }

    @NotNull
    public ExtractMethodInfoHelper getHelper() {
      return myHelper;
    }

    public String getEnteredName() {
      return myEnteredName;
    }
  }
}
