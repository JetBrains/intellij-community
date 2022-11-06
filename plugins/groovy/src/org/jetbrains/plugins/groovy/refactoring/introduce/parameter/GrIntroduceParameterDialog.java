// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.HelpID;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.GrParameterTablePanel;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromClosureProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureProcessorBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.ui.GrMethodSignatureComponent;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class GrIntroduceParameterDialog extends DialogWrapper {
  private GrTypeComboBox myTypeComboBox;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myDelegateViaOverloadingMethodCheckBox;
  private JBRadioButton myDoNotReplaceRadioButton;
  private JBRadioButton myReplaceFieldsInaccessibleInRadioButton;
  private JBRadioButton myReplaceAllFieldsRadioButton;
  private JPanel myGetterPanel;
  private final IntroduceParameterInfo myInfo;
  private final Object2IntMap<JCheckBox> toRemoveCBs;

  private GrMethodSignatureComponent mySignature;
  private GrParameterTablePanel myTable;
  private JPanel mySignaturePanel;
  private JCheckBox myForceReturnCheckBox;
  private final Project myProject;
  private final PsiFile myFile;

  private final boolean myCanIntroduceSimpleParameter;

  public GrIntroduceParameterDialog(IntroduceParameterInfo info) {
    super(info.getProject(), true);
    myInfo = info;
    myFile = info.getContext().getContainingFile();
    myProject = info.getProject();
    myCanIntroduceSimpleParameter = GroovyIntroduceParameterUtil.findExpr(myInfo) != null ||
                                    GroovyIntroduceParameterUtil.findVar(myInfo) != null ||
                                    findStringPart() != null;

    Object2IntMap<GrParameter> parametersToRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    toRemoveCBs = new Object2IntOpenHashMap<>(parametersToRemove.size());
    for (GrParameter p : parametersToRemove.keySet()) {
      JCheckBox cb = new JCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", p.getName()));
      toRemoveCBs.put(cb, parametersToRemove.getInt(p));
      cb.setSelected(true);
    }

    init();
  }

  @Override
  protected void init() {
    super.init();

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    initReplaceFieldsWithGetters(settings);

    myDeclareFinalCheckBox.setSelected(hasFinalModifier());
    myDelegateViaOverloadingMethodCheckBox.setVisible(myInfo.getToSearchFor() != null);

    setTitle(RefactoringBundle.message("introduce.parameter.title"));

    myTable.init(myInfo);

    final GrParameter[] parameters = myInfo.getToReplaceIn().getParameters();
    for (Object2IntMap.Entry<JCheckBox> entry : toRemoveCBs.object2IntEntrySet()) {
      entry.getKey().setSelected(true);

      final GrParameter param = parameters[entry.getIntValue()];
      final ParameterInfo pinfo = findParamByOldName(param.getName());
      if (pinfo != null) {
        pinfo.passAsParameter = false;
      }
    }

    updateSignature();

    if (myCanIntroduceSimpleParameter) {
      mySignaturePanel.setVisible(false);

      //action to hide signature panel if we have variants to introduce simple parameter
      myTypeComboBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          mySignaturePanel.setVisible(myTypeComboBox.isClosureSelected());
          pack();
        }
      });
    }

    final PsiType closureReturnType = inferClosureReturnType();
    if (PsiType.VOID.equals(closureReturnType)) {
      myForceReturnCheckBox.setEnabled(false);
      myForceReturnCheckBox.setSelected(false);
    }
    else {
      myForceReturnCheckBox.setSelected(isForceReturn());
    }

    if (myInfo.getToReplaceIn() instanceof GrClosableBlock) {
      myDelegateViaOverloadingMethodCheckBox.setEnabled(false);
      myDelegateViaOverloadingMethodCheckBox.setToolTipText(GroovyBundle.message("introduce.parameter.delegating.unavailable.tooltip"));
    }


    pack();
  }

  private static boolean isForceReturn() {
    return GroovyApplicationSettings.getInstance().FORCE_RETURN;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel north = new JPanel();
    north.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));

    final JPanel namePanel = createNamePanel();
    namePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(namePanel);

    createCheckBoxes(north);

    myGetterPanel = createFieldPanel();
    myGetterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(myGetterPanel);

    final JPanel root = new JPanel(new BorderLayout());
    mySignaturePanel = createSignaturePanel();
    root.add(mySignaturePanel, BorderLayout.CENTER);
    root.add(north, BorderLayout.NORTH);

    return root;
  }

  private JPanel createSignaturePanel() {
    mySignature = new GrMethodSignatureComponent("", myProject);
    myTable = new GrParameterTablePanel() {
      @Override
      protected void updateSignature() {
        GrIntroduceParameterDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        GrIntroduceParameterDialog.this.doCancelAction();
      }
    };

    mySignature.setBorder(IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false));

    Splitter splitter = new Splitter(true);

    splitter.setFirstComponent(myTable);
    splitter.setSecondComponent(mySignature);

    mySignature.setPreferredSize(JBUI.size(500, 100));
    mySignature.setSize(JBUI.size(500, 100));

    splitter.setShowDividerIcon(false);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(splitter, BorderLayout.CENTER);
    myForceReturnCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.parameter.explicit.return.statement.option.label")
    ));
    panel.add(myForceReturnCheckBox, BorderLayout.NORTH);

    return panel;
  }

  private JPanel createFieldPanel() {
    myDoNotReplaceRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.parameter.do.not.replace.option.label")
    ));
    myReplaceFieldsInaccessibleInRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.parameter.replace.inaccessible.fields.option.label")
    ));
    myReplaceAllFieldsRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.parameter.replace.all.fields.option.label")
    ));

    myDoNotReplaceRadioButton.setFocusable(false);
    myReplaceFieldsInaccessibleInRadioButton.setFocusable(false);
    myReplaceAllFieldsRadioButton.setFocusable(false);

    final ButtonGroup group = new ButtonGroup();
    group.add(myDoNotReplaceRadioButton);
    group.add(myReplaceFieldsInaccessibleInRadioButton);
    group.add(myReplaceAllFieldsRadioButton);

    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(myDoNotReplaceRadioButton);
    panel.add(myReplaceFieldsInaccessibleInRadioButton);
    panel.add(myReplaceAllFieldsRadioButton);

    panel.setBorder(IdeBorderFactory.createTitledBorder(GroovyBundle.message("introduce.parameter.replace.fields.border.title")));
    return panel;
  }

  private JPanel createNamePanel() {
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(1, 1, 1, 1);
    final JPanel namePanel = new JPanel(new GridBagLayout());

    final JLabel typeLabel = new JLabel(UIUtil.replaceMnemonicAmpersand(GroovyBundle.message("introduce.variable.type.label")));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(typeLabel, c);

    myTypeComboBox = createTypeComboBox(GroovyIntroduceParameterUtil.findVar(myInfo), GroovyIntroduceParameterUtil.findExpr(myInfo), findStringPart());
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myTypeComboBox, c);
    typeLabel.setLabelFor(myTypeComboBox);

    final JLabel nameLabel = new JLabel(UIUtil.replaceMnemonicAmpersand(GroovyBundle.message("introduce.variable.name.label")));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(nameLabel, c);

    myNameSuggestionsField = createNameField(GroovyIntroduceParameterUtil.findVar(myInfo));
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myNameSuggestionsField, c);
    nameLabel.setLabelFor(myNameSuggestionsField);

    GrTypeComboBox.registerUpDownHint(myNameSuggestionsField, myTypeComboBox);

    return namePanel;
  }


  private void createCheckBoxes(JPanel panel) {
    myDeclareFinalCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand(GroovyBundle.message(
      "introduce.variable.declare.final.label")));
    myDeclareFinalCheckBox.setFocusable(false);
    panel.add(myDeclareFinalCheckBox);

    myDelegateViaOverloadingMethodCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.parameter.delegate.via.overload")
    ));
    myDelegateViaOverloadingMethodCheckBox.setFocusable(false);
    panel.add(myDelegateViaOverloadingMethodCheckBox);

    for (JCheckBox cb : toRemoveCBs.keySet()) {
      cb.setFocusable(false);
      panel.add(cb);
    }
  }

  private GrTypeComboBox createTypeComboBox(GrVariable var, GrExpression expr, StringPartInfo stringPartInfo) {
    GrTypeComboBox box;
    if (var != null) {
      box = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType(), var);
    }
    else if (expr != null) {
      box = GrTypeComboBox.createTypeComboBoxFromExpression(expr);
    }
    else if (stringPartInfo != null) {
      box = GrTypeComboBox.createTypeComboBoxFromExpression(stringPartInfo.getLiteral());
    }
    else {
      box = GrTypeComboBox.createEmptyTypeComboBox();
    }

    box.addClosureTypesFrom(inferClosureReturnType(), myInfo.getContext());
    if (expr == null && var == null && stringPartInfo == null) {
      box.setSelectedIndex(box.getItemCount() - 1);
    }
    return box;
  }

  @Nullable
  private PsiType inferClosureReturnType() {
    final ExtractClosureHelperImpl mockHelper =
      new ExtractClosureHelperImpl(myInfo, "__test___n_", false, new IntArrayList(), false,
                                   IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
    return WriteAction.compute(() -> ExtractClosureProcessorBase.generateClosure(mockHelper).getReturnType());
  }

  private NameSuggestionsField createNameField(GrVariable var) {
    List<String> names = new ArrayList<>();
    if (var != null) {
      names.add(var.getName());
    }
    names.addAll(suggestNames());

    return new NameSuggestionsField(ArrayUtilRt.toStringArray(names), myProject, GroovyFileType.GROOVY_FILE_TYPE);
  }

  private void initReplaceFieldsWithGetters(JavaRefactoringSettings settings) {
    final PsiField[] usedFields = GroovyIntroduceParameterUtil.findUsedFieldsWithGetters(myInfo.getStatements(), getContainingClass());
    myGetterPanel.setVisible(usedFields.length > 0);
    switch (settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS) {
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL -> myReplaceAllFieldsRadioButton.setSelected(true);
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE ->
        myReplaceFieldsInaccessibleInRadioButton.setSelected(true);
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE -> myDoNotReplaceRadioButton.setSelected(true);
    }
  }

  private void updateSignature() {
    StringBuilder b = new StringBuilder();
    b.append("{ ");
    String[] params = ExtractUtil.getParameterString(myInfo, false);
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
  protected ValidationInfo doValidate() {
    final String text = getEnteredName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      return new ValidationInfo(GroovyRefactoringBundle.message("name.is.wrong", text), myNameSuggestionsField);
    }

    if (myTypeComboBox.isClosureSelected()) {
      final Ref<ValidationInfo> info = new Ref<>();
      for (Object2IntMap.Entry<JCheckBox> entry : toRemoveCBs.object2IntEntrySet()) {
        if (!entry.getKey().isSelected()) {
          continue;
        }

        final GrParameter param = myInfo.getToReplaceIn().getParameters()[entry.getIntValue()];
        final ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo == null || !pinfo.passAsParameter) {
          continue;
        }

        final String message = GroovyRefactoringBundle
          .message("you.cannot.pass.as.parameter.0.because.you.remove.1.from.base.method", pinfo.getName(), param.getName());
        info.set(new ValidationInfo(message));
        break;
      }

      if (info.get() != null) {
        return info.get();
      }
    }

    return null;
  }

  @Nullable
  private ParameterInfo findParamByOldName(String name) {
    for (ParameterInfo info : myInfo.getParameterInfos()) {
      if (name.equals(info.getOriginalName())) return info;
    }
    return null;
  }

  @Nullable
  private PsiClass getContainingClass() {
    final GrParameterListOwner toReplaceIn = myInfo.getToReplaceIn();
    if (toReplaceIn instanceof GrMethod) {
      return ((GrMethod)toReplaceIn).getContainingClass();
    }
    else {
      return PsiTreeUtil.getContextOfType(toReplaceIn, PsiClass.class);
    }
  }

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? JavaCodeStyleSettings.getInstance(myFile).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  public void doOKAction() {
    saveSettings();

    super.doOKAction();

    final GrParameterListOwner toReplaceIn = myInfo.getToReplaceIn();

    final GrExpression expr = GroovyIntroduceParameterUtil.findExpr(myInfo);
    final GrVariable var = GroovyIntroduceParameterUtil.findVar(myInfo);
    final StringPartInfo stringPart = findStringPart();

    if (myTypeComboBox.isClosureSelected() || expr == null && var == null && stringPart == null) {
      GrIntroduceParameterSettings settings = new ExtractClosureHelperImpl(myInfo,
                                                                           getEnteredName(),
                                                                           myDeclareFinalCheckBox.isSelected(),
                                                                           getParametersToRemove(),
                                                                           myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                           getReplaceFieldsWithGetter(),
                                                                           myForceReturnCheckBox.isSelected(),
                                                                           false,
                                                                           myTypeComboBox.getSelectedType() == null);
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new ExtractClosureFromMethodProcessor(settings));
      }
      else {
        invokeRefactoring(new ExtractClosureFromClosureProcessor(settings));
      }
    }
    else {

      GrIntroduceParameterSettings settings = new GrIntroduceExpressionSettingsImpl(myInfo,
                                                                                    getEnteredName(),
                                                                                    myDeclareFinalCheckBox.isSelected(),
                                                                                    getParametersToRemove(),
                                                                                    myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                                    getReplaceFieldsWithGetter(),
                                                                                    expr,
                                                                                    var,
                                                                                    myTypeComboBox.getSelectedType(),
                                                                                    var != null, true, myForceReturnCheckBox.isSelected());
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new GrIntroduceParameterProcessor(settings));
      }
      else {
        invokeRefactoring(new GrIntroduceClosureParameterProcessor(settings));
      }
    }
  }

  private String getEnteredName() {
    return myNameSuggestionsField.getEnteredName();
  }

  private void saveSettings() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_CREATE_FINALS = myDeclareFinalCheckBox.isSelected();
    if (myGetterPanel.isVisible()) {
      settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS = getReplaceFieldsWithGetter();
    }
    if (myForceReturnCheckBox.isEnabled() && mySignaturePanel.isVisible()) {
      GroovyApplicationSettings.getInstance().FORCE_RETURN = myForceReturnCheckBox.isSelected();
    }
  }

  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    final Runnable prepareSuccessfulCallback = () -> close(DialogWrapper.OK_EXIT_CODE);
    processor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
    processor.setPreviewUsages(false);
    processor.run();
  }

  @NotNull
  public LinkedHashSet<String> suggestNames() {
    GrVariable var = GroovyIntroduceParameterUtil.findVar(myInfo);
    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(myInfo);
    StringPartInfo stringPart = findStringPart();

    return GroovyIntroduceParameterUtil.suggestNames(var, expr, stringPart, myInfo.getToReplaceIn(), myProject);
  }

  @MagicConstant(valuesFromClass = IntroduceParameterRefactoring.class)
  private int getReplaceFieldsWithGetter() {
    if (myDoNotReplaceRadioButton.isSelected()) return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE;
    if (myReplaceFieldsInaccessibleInRadioButton.isSelected()) return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    if (myReplaceAllFieldsRadioButton.isSelected()) return IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL;
    throw new GrRefactoringError("no check box selected");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.GROOVY_INTRODUCE_PARAMETER;
  }

  private IntList getParametersToRemove() {
    IntList list=new IntArrayList();
    for (JCheckBox o : toRemoveCBs.keySet()) {
      if (o.isSelected()) {
        list.add(toRemoveCBs.getInt(o));
      }
    }
    return list;
  }

  private StringPartInfo findStringPart() {
    return myInfo.getStringPartInfo();
  }

}
