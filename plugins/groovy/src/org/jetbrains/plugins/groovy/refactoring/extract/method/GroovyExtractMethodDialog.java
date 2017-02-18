/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.DuplicatesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.GrParameterTablePanel;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.ui.GrMethodSignatureComponent;
import org.jetbrains.plugins.groovy.refactoring.ui.GroovyComboboxVisibilityPanel;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;

/**
 * @author ilyas
 */
public class GroovyExtractMethodDialog extends DialogWrapper {
  private final ExtractMethodInfoHelper myHelper;

  private final EventListenerList myListenerList = new EventListenerList();

  private JPanel contentPane;
  private EditorTextField myNameField;
  private JCheckBox myCbSpecifyType;
  private JLabel myNameLabel;
  private MethodSignatureComponent mySignature;
  private ComboBoxVisibilityPanel<String> myVisibilityPanel;
  private Splitter mySplitter;
  private JCheckBox myForceReturnCheckBox;
  private GrParameterTablePanel myParameterTablePanel;
  private final Project myProject;

  public GroovyExtractMethodDialog(InitialInfo info, PsiClass owner) {
    super(info.getProject(), true);
    myProject = info.getProject();
    myHelper = new ExtractMethodInfoHelper(info, "", owner, false);

    myParameterTablePanel.init(myHelper);

    setModal(true);
    setTitle(GroovyExtractMethodHandler.REFACTORING_NAME);
    init();
    setUpNameField();
    setUpDialog();
    update();
  }

  @Override
  protected void init() {
    super.init();
    mySplitter.setOrientation(true);
    mySplitter.setShowDividerIcon(false);
    mySplitter.setFirstComponent(myParameterTablePanel);
    mySplitter.setSecondComponent(mySignature);
  }

  @Override
  protected void doOKAction() {
    myHelper.setForceReturn(myForceReturnCheckBox.isSelected());
    String name = getEnteredName();
    if (name == null) return;
    GrMethod method = ExtractUtil.createMethod(myHelper);
    if (method != null && !validateMethod(method, myHelper)) {
      return;
    }
    final GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    if (myCbSpecifyType.isEnabled()) {
      settings.EXTRACT_METHOD_SPECIFY_TYPE = myCbSpecifyType.isSelected();
    }
    if (myForceReturnCheckBox.isEnabled()) {
      settings.FORCE_RETURN = myForceReturnCheckBox.isSelected();
    }
    settings.EXTRACT_METHOD_VISIBILITY = myVisibilityPanel.getVisibility();
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
      @Override
      public void stateChanged(ChangeEvent e) {
        myHelper.setSpecifyType(myCbSpecifyType.isSelected());
        updateSignature();
      }
    });

    myHelper.setSpecifyType(myCbSpecifyType.isSelected());
    myHelper.setVisibility(myVisibilityPanel.getVisibility());
    myNameLabel.setLabelFor(myNameField);

    final PsiType type = myHelper.getOutputType();
    if (!PsiType.VOID.equals(type)) {
      myForceReturnCheckBox.setSelected(GroovyApplicationSettings.getInstance().FORCE_RETURN);
    }
    else {
      myForceReturnCheckBox.setEnabled(false);
      myForceReturnCheckBox.setSelected(false);
    }
  }

  private void setUpNameField() {
    myNameLabel.setLabelFor(myNameField);
    myNameField.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    myListenerList.add(DataChangedListener.class, new DataChangedListener());
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
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
  }

  @Override
  protected ValidationInfo doValidate() {
    return null;
  }

  @Nullable
  protected String getEnteredName() {
    String text = myNameField.getText();
    if (text != null && !text.trim().isEmpty()) {
      return text.trim();
    }
    else {
      return null;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_METHOD);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  private void createUIComponents() {
    mySignature = new GrMethodSignatureComponent("", myProject);
    mySignature.setPreferredSize(JBUI.size(500, 100));
    mySignature.setMinimumSize(JBUI.size(500, 100));
    mySignature.setBorder(
      IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false));
    mySignature.setFocusable(false);

    myNameField = new EditorTextField("", myProject, GroovyFileType.GROOVY_FILE_TYPE);
    myVisibilityPanel = new GroovyComboboxVisibilityPanel();

    String visibility = GroovyApplicationSettings.getInstance().EXTRACT_METHOD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    myVisibilityPanel.setVisibility(visibility);
    myVisibilityPanel.addListener(new ChangeListener(){
      @Override
      public void stateChanged(ChangeEvent e) {
        myHelper.setVisibility(myVisibilityPanel.getVisibility());
        updateSignature();
      }
    });

    myParameterTablePanel = new GrParameterTablePanel() {
      @Override
      protected void updateSignature(){
        GroovyExtractMethodDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction(){
        GroovyExtractMethodDialog.this.clickDefaultButton();
      }

      @Override
      protected void doCancelAction(){
        GroovyExtractMethodDialog.this.doCancelAction();
      }
    };
  }

  private static boolean validateMethod(GrMethod method, ExtractMethodInfoHelper helper) {
    ArrayList<String> conflicts = new ArrayList<>();
    PsiClass owner = helper.getOwner();
    PsiMethod[] methods = ArrayUtil.mergeArrays(owner.getAllMethods(), new PsiMethod[]{method}, PsiMethod.ARRAY_FACTORY);
    final Map<PsiMethod, List<PsiMethod>> map = DuplicatesUtil.factorDuplicates(methods, new TObjectHashingStrategy<PsiMethod>() {
      @Override
      public int computeHashCode(PsiMethod method) {
        return method.getSignature(PsiSubstitutor.EMPTY).hashCode();
      }

      @Override
      public boolean equals(PsiMethod method1, PsiMethod method2) {
        return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
      }
    });

    List<PsiMethod> list = map.get(method);
    if (list == null) return true;
    for (PsiMethod psiMethod : list) {
      if (psiMethod != method) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return true;
        String message = containingClass instanceof GroovyScriptClass ?
            GroovyRefactoringBundle.message("method.is.already.defined.in.script", GroovyRefactoringUtil.getMethodSignature(method),
                CommonRefactoringUtil.htmlEmphasize(containingClass.getQualifiedName())) :
            GroovyRefactoringBundle.message("method.is.already.defined.in.class", GroovyRefactoringUtil.getMethodSignature(method),
                CommonRefactoringUtil.htmlEmphasize(containingClass.getQualifiedName()));
        conflicts.add(message);
      }
    }

    return conflicts.size() <= 0 || reportConflicts(conflicts, helper.getProject());
  }

  private static boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    return conflictsDialog.showAndGet();
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
    if (mySignature == null) return;
    @NonNls StringBuilder buffer = new StringBuilder();
    String modifier = ExtractUtil.getModifierString(myHelper);
    buffer.append(modifier);
    buffer.append(ExtractUtil.getTypeString(myHelper, true, modifier));

    final String _name = getEnteredName();
    String name = _name == null ? "" : _name;
    ExtractUtil.appendName(buffer, name);

    buffer.append("(");
    String[] params = ExtractUtil.getParameterString(myHelper, false);
    if (params.length > 0) {
      String INDENT = "    ";
      buffer.append("\n");
      for (String param : params) {
        buffer.append(INDENT).append(param).append("\n");
      }
    }
    buffer.append(")");
    mySignature.setSignature(buffer.toString());
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

    @Override
    @NotNull
    public ExtractMethodInfoHelper getHelper() {
      return myHelper;
    }

    @Override
    public String getEnteredName() {
      return myEnteredName;
    }
  }
}
