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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterTablePanel;
import org.jetbrains.plugins.groovy.refactoring.ui.GrMethodSignatureComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Max Medvedev
 */
public class ExtractClosureDialog extends DialogWrapper {

  private final ParameterTablePanel myTable;
  private final GrMethodSignatureComponent mySignature;
  private final EditorTextField myNameField;

  private final ExtractClosureHelper myHelper;
  private final JBCheckBox myFinalCB;
  private final JBCheckBox myGenerateDelegateCB;

  TObjectIntHashMap<JBCheckBox> toRemoveCBs;

  public ExtractClosureDialog(InitialInfo initialInfo, GrParametersOwner owner, PsiElement toSearchFor) {
    super(initialInfo.getProject());

    myHelper = new ExtractClosureHelper(initialInfo, owner, toSearchFor, "", false);

    setTitle(ExtractClosureHandler.EXTRACT_CLOSURE);

    myTable = new ParameterTablePanel() {
      @Override
      protected void updateSignature() {
        ExtractClosureDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        ExtractClosureDialog.this.doCancelAction();
      }
    };

    mySignature = new GrMethodSignatureComponent("", initialInfo.getProject());
    myNameField = new EditorTextField("", initialInfo.getProject(), GroovyFileType.GROOVY_FILE_TYPE);
    myFinalCB = new JBCheckBox(UIUtil.replaceMnemonicAmpersand("Declare &final"));
    myFinalCB.setFocusable(false);
    myGenerateDelegateCB = new JBCheckBox(UIUtil.replaceMnemonicAmpersand("De&legate via overloading method"));
    myGenerateDelegateCB.setFocusable(false);

    TObjectIntHashMap<GrParameter> parametersToRemove = findParametersToRemove(myHelper);
    toRemoveCBs = new TObjectIntHashMap<JBCheckBox>(parametersToRemove.size());
    for (Object p : parametersToRemove.keys()) {
      JBCheckBox cb = new JBCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", ((GrParameter)p).getName()));
      cb.setFocusable(false);
      toRemoveCBs.put(cb, parametersToRemove.get((GrParameter)p));
    }

    init();
  }

  @Override
  protected ValidationInfo doValidate() {
    final String text = myNameField.getText().trim();
    if (!StringUtil.isJavaIdentifier(text)) {
      return new ValidationInfo(GroovyRefactoringBundle.message("name.is.wrong", text), myNameField);
    }

    final Ref<ValidationInfo> info = new Ref<ValidationInfo>();
    toRemoveCBs.forEachEntry(new TObjectIntProcedure<JBCheckBox>() {
      @Override
      public boolean execute(JBCheckBox checkbox, int index) {
        if (!checkbox.isSelected()) return true;


        final GrParameter param = myHelper.getOwner().getParameters()[index];
        final ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo == null || !pinfo.passAsParameter()) return true;

        final String message = GroovyRefactoringBundle.message("you.cannot.pass.as.parameter.0.because.you.remove.1.from.base.method",
                                                               pinfo.getName(), param.getName());
        info.set(new ValidationInfo(message));
        return false;
      }
    });
    return info.get();
  }

  @Nullable
  private ParameterInfo findParamByOldName(String name) {
    for (ParameterInfo info : myHelper.getParameterInfos()) {
      if (name.equals(info.getOldName())) return info;
    }
    return null;
  }

  private void updateSignature() {
    StringBuilder b = new StringBuilder();
    b.append("{ ");
    String[] params = ExtractUtil.getParameterString(myHelper, false);
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        b.append("  ");
      }
      b.append(params[i]);
      b.append('\n');
    }
    b.append(" ->\n}");
    mySignature.setSignature(b.toString());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected void init() {
    super.init();

    myTable.init(myHelper);

    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final Boolean settingsFinals = refactoringSettings.INTRODUCE_PARAMETER_CREATE_FINALS;
    myFinalCB.setSelected(settingsFinals == null ?
                          CodeStyleSettingsManager.getSettings(myHelper.getProject()).GENERATE_FINAL_PARAMETERS :
                          settingsFinals.booleanValue());
    myGenerateDelegateCB.setSelected(false);

    final GrParameter[] parameters = myHelper.getOwner().getParameters();
    toRemoveCBs.forEachEntry(new TObjectIntProcedure<JBCheckBox>() {
      @Override
      public boolean execute(JBCheckBox checkbox, int index) {
        checkbox.setSelected(true);

        final GrParameter param = parameters[index];
        final ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo != null) {
          pinfo.setPassAsParameter(false);
        }
        return true;
      }
    });
    updateSignature();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JBLabel nameLabel = new JBLabel();
    panel.add(nameLabel, BorderLayout.NORTH);
    panel.add(myNameField, BorderLayout.CENTER);
    nameLabel.setText(UIUtil.replaceMnemonicAmpersand("Parameter &name:"));
    nameLabel.setLabelFor(myNameField);

    final JPanel checkBoxPanel = new JPanel();
    checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));

    checkBoxPanel.add(myFinalCB);
    checkBoxPanel.add(myGenerateDelegateCB);
    for (Object cb : toRemoveCBs.keys()) {
      checkBoxPanel.add((Component)cb);
    }
    panel.add(checkBoxPanel, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel signaturePanel = new JPanel(new BorderLayout());
    signaturePanel.setBorder(
      IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false, false, true));
    signaturePanel.add(mySignature, BorderLayout.CENTER);

    Splitter splitter = new Splitter(true);

    splitter.setFirstComponent(myTable);
    splitter.setSecondComponent(signaturePanel);

    mySignature.setPreferredSize(new Dimension(500, 100));
    mySignature.setSize(new Dimension(500, 100));

    splitter.setShowDividerIcon(false);
    return splitter;
  }

  @Override
  protected void doOKAction() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_CREATE_FINALS = Boolean.valueOf(myFinalCB.isSelected());

    myHelper.setName(myNameField.getText());
    myHelper.setDeclareFinal(myFinalCB.isSelected());
    myHelper.setGenerateDelegate(myGenerateDelegateCB.isSelected());

    TIntArrayList list = new TIntArrayList();
    for (Object cb : toRemoveCBs.keys()) {
      final JBCheckBox checkbox = (JBCheckBox)cb;
      if (checkbox.isSelected()) {
        list.add(toRemoveCBs.get(checkbox));
      }
    }
    myHelper.setToRemove(list);
    super.doOKAction();
  }

  public ExtractClosureHelper getHelper() {
    return myHelper;
  }

  private static TObjectIntHashMap<GrParameter> findParametersToRemove(ExtractClosureHelper helper) {
    final TObjectIntHashMap<GrParameter> result = new TObjectIntHashMap<GrParameter>();

    final GrStatement[] statements = helper.getStatements();
    final int start = statements[0].getTextRange().getStartOffset();
    final int end = statements[statements.length - 1].getTextRange().getEndOffset();

    GrParameter[] parameters = helper.getOwner().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      if (shouldRemove(parameter, start, end)) {
        result.put(parameter, i);
      }
    }
    return result;
  }

  private static boolean shouldRemove(GrParameter parameter, int start, int end) {
    for (PsiReference reference : ReferencesSearch.search(parameter)) {
      final PsiElement element = reference.getElement();
      if (element == null) continue;

      final int offset = element.getTextRange().getStartOffset();
      if (offset < start || end <= offset) {
        return false;
      }
    }
    return true;
  }
}
