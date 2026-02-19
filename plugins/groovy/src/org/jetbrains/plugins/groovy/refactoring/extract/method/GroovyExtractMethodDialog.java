// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
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

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class GroovyExtractMethodDialog extends DialogWrapper {
  private final ExtractMethodInfoHelper myHelper;

  private final EventListenerList myListenerList = new EventListenerList();

  private final JPanel contentPane;
  private final EditorTextField myNameField;
  private final JCheckBox myCbSpecifyType;
  private final JLabel myNameLabel;
  private final MethodSignatureComponent mySignature;
  private final ComboBoxVisibilityPanel<String> myVisibilityPanel;
  private final Splitter mySplitter;
  private final JCheckBox myForceReturnCheckBox;
  private final GrParameterTablePanel myParameterTablePanel;

  public GroovyExtractMethodDialog(InitialInfo info, PsiClass owner) {
    super(info.getProject(), true);

    Project project = info.getProject();

    {
      mySignature = new GrMethodSignatureComponent("", project);
      mySignature.setPreferredSize(JBUI.size(500, 100));
      mySignature.setMinimumSize(JBUI.size(500, 100));
      mySignature.setBorder(
        IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false));
      mySignature.setFocusable(false);

      myNameField = new EditorTextField("", project, GroovyFileType.GROOVY_FILE_TYPE);
      myVisibilityPanel = new GroovyComboboxVisibilityPanel();

      String visibility = GroovyApplicationSettings.getInstance().EXTRACT_METHOD_VISIBILITY;
      if (visibility == null) {
        visibility = PsiModifier.PRIVATE;
      }
      myVisibilityPanel.setVisibility(visibility);
      myVisibilityPanel.addListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          myHelper.setVisibility(myVisibilityPanel.getVisibility());
          updateSignature();
        }
      });

      myParameterTablePanel = new GrParameterTablePanel() {
        @Override
        protected void updateSignature() {
          GroovyExtractMethodDialog.this.updateSignature();
        }

        @Override
        protected void doEnterAction() {
          GroovyExtractMethodDialog.this.clickDefaultButton();
        }

        @Override
        protected void doCancelAction() {
          GroovyExtractMethodDialog.this.doCancelAction();
        }
      };
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      contentPane = new JPanel();
      contentPane.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
      myCbSpecifyType = new JCheckBox();
      this.$$$loadButtonText$$$(myCbSpecifyType, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "specify.type.label"));
      contentPane.add(myCbSpecifyType, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      panel1.add(myVisibilityPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      myNameLabel = new JLabel();
      this.$$$loadLabelText$$$(myNameLabel, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "name.label"));
      panel2.add(myNameLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      panel2.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      panel2.add(myNameField, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                  new Dimension(150, -1), null, 0, false));
      mySplitter = new Splitter();
      contentPane.add(mySplitter, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                      null, null, 0, false));
      myForceReturnCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(myForceReturnCheckBox, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle",
                                                                                       "extract.method.dialog.explicit.return.checkbox"));
      contentPane.add(myForceReturnCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    }
    myHelper = new ExtractMethodInfoHelper(info, "", owner, false);

    myParameterTablePanel.init(myHelper);

    setModal(true);
    setTitle(GroovyExtractMethodHandler.getRefactoringName());
    init();
    setUpNameField();
    setUpDialog();
    update();
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
  public JComponent $$$getRootComponent$$$() { return contentPane; }

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
    if (!validateMethod(myHelper)) {
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
    if (!PsiTypes.voidType().equals(type)) {
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
      public void documentChanged(@NotNull DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    myListenerList.add(DataChangedListener.class, new DataChangedListener());
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getContentPane() {
    return contentPane;
  }

  protected @NotNull ExtractMethodInfoHelper getHelper() {
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

  protected @Nullable String getEnteredName() {
    String text = myNameField.getText();
    if (!text.trim().isEmpty()) {
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
  protected String getHelpId() {
    return HelpID.EXTRACT_METHOD;
  }

  private static boolean validateMethod(ExtractMethodInfoHelper helper) {
    MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts = new MultiMap<>();
    GrMethod method = ExtractUtil.createMethod(helper);
    PsiClass owner = helper.getOwner();
    PsiMethod[] methods = ArrayUtil.mergeArrays(owner.getAllMethods(), new PsiMethod[]{method}, PsiMethod.ARRAY_FACTORY);
    final Map<PsiMethod, List<PsiMethod>> map = DuplicatesUtil.factorDuplicates(methods, new HashingStrategy<>() {
      @Override
      public int hashCode(@Nullable PsiMethod method) {
        return method == null ? 0 : method.getSignature(PsiSubstitutor.EMPTY).hashCode();
      }

      @Override
      public boolean equals(@Nullable PsiMethod method1, @Nullable PsiMethod method2) {
        return method1 == method2 ||
               (method1 != null &&
                method2 != null &&
                method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY)));
      }
    });

    List<PsiMethod> list = map.get(method);
    if (list == null) return true;
    for (PsiMethod psiMethod : list) {
      if (psiMethod != method) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return true;
        String message = containingClass instanceof GroovyScriptClass
                         ? GroovyRefactoringBundle.message("method.is.already.defined.in.script",
                                                           GroovyRefactoringUtil.getMethodSignature(psiMethod),
                                                           RefactoringUIUtil.getDescription(containingClass, false))
                         : GroovyRefactoringBundle.message("method.is.already.defined.in.class",
                                                           GroovyRefactoringUtil.getMethodSignature(psiMethod),
                                                           RefactoringUIUtil.getDescription(containingClass, false));
        conflicts.putValue(psiMethod, message);
      }
    }

    return conflicts.isEmpty() || BaseRefactoringProcessor.processConflicts(helper.getProject(), conflicts);
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

    MyExtractMethodSettings(GroovyExtractMethodDialog dialog) {
      myHelper = dialog.getHelper();
      myEnteredName = dialog.getEnteredName();
    }

    @Override
    public @NotNull ExtractMethodInfoHelper getHelper() {
      return myHelper;
    }

    @Override
    public String getEnteredName() {
      return myEnteredName;
    }
  }
}
