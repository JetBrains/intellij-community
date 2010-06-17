/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.util.Icons;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

public class JUnitConfigurable extends SettingsEditor<JUnitConfiguration> {
  // Garbage
  private JRadioButton myAllInPackageButton;
  private JRadioButton myClassButton;
  private JRadioButton myTestMethodButton;
  private JRadioButton myTestPatternButton;
  private JComponent myPackagePanel;
  private LabeledComponent<TextFieldWithBrowseButton> myPackage;
  private LabeledComponent<JPanel> myPattern;
  private LabeledComponent<TextFieldWithBrowseButton> myClass;
  private LabeledComponent<TextFieldWithBrowseButton> myMethod;

  // Fields
  private JPanel myWholePanel;
  private LabeledComponent<JComboBox> myModule;
  private CommonJavaParametersPanel myCommonJavaParameters;
  private JRadioButton myWholeProjectScope;
  private JRadioButton mySingleModuleScope;
  private JRadioButton myModuleWDScope;
  private TextFieldWithBrowseButton myPatternTextField;

  private static final ArrayList<TIntArrayList> ourEnabledFields = new ArrayList<TIntArrayList>(Arrays.asList(new TIntArrayList[]{
    new TIntArrayList(new int[]{0}),
    new TIntArrayList(new int[]{1}),
    new TIntArrayList(new int[]{1, 2}),
    new TIntArrayList(new int[]{3})
  }));
  private final ConfigurationModuleSelector myModuleSelector;
  private final JRadioButton[] myRadioButtons = new JRadioButton[4];
  private final LabeledComponent[] myTestLocations = new LabeledComponent[4];
  private final JUnitConfigurationModel myModel;

  private final BrowseModuleValueActionListener[] myBrowsers;
  private AlternativeJREPanel myAlternativeJREPanel;

  public JUnitConfigurable(final Project project) {
    myModel = new JUnitConfigurationModel(project);
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    myBrowsers = new BrowseModuleValueActionListener[]{
      new PackageChooserActionListener(project),
      new TestClassBrowser(project),
      new MethodBrowser(project),
      new TestsChooserActionListener(project)
    };
    // Garbage support
    myRadioButtons[JUnitConfigurationModel.ALL_IN_PACKAGE] = myAllInPackageButton;
    myRadioButtons[JUnitConfigurationModel.CLASS] = myClassButton;
    myRadioButtons[JUnitConfigurationModel.METHOD] = myTestMethodButton;
    myRadioButtons[JUnitConfigurationModel.PATTERN] = myTestPatternButton;
    myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
    myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
    myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton();
    myPatternTextField.setButtonIcon(Icons.ADD_ICON);
    panel.add(myPatternTextField, BorderLayout.CENTER);
    final FixedSizeButton editBtn = new FixedSizeButton();
    editBtn.setIcon(IconLoader.getIcon("/actions/showViewer.png"));
    editBtn.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myPatternTextField.getTextField(), "Configure suite tests", "EditParametersPopupWindow");
      }
    });
    panel.add(editBtn, BorderLayout.EAST);
    myTestLocations[JUnitConfigurationModel.PATTERN] = myPattern;
    // Done

    myModel.setListener(this);

    addRadioButtonsListeners(myRadioButtons, new ChangeListener() {
        public void stateChanged(final ChangeEvent e) {
          final ButtonModel buttonModel = (ButtonModel)e.getSource();
          if (buttonModel.isSelected()) {
            for (int i = 0; i < myRadioButtons.length; i++)
              if (buttonModel == myRadioButtons[i].getModel()) {
                myModel.setType(i);
                break;
              }
          }
          changePanel();
        }
      });
    myModel.setType(JUnitConfigurationModel.CLASS);
    installDocuments();
    addRadioButtonsListeners(new JRadioButton[]{myWholeProjectScope, mySingleModuleScope, myModuleWDScope}, null);
    myWholeProjectScope.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        onScopeChanged();
      }
    });

    myCommonJavaParameters.setProgramParametersLabel(ExecutionBundle.message("junit.configuration.test.runner.parameters.label"));
  }

  public void applyEditorTo(final JUnitConfiguration configuration) {
    myModel.apply(getModuleSelector().getModule(), configuration);
    applyHelpersTo(configuration);
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    if (myWholeProjectScope.isSelected()) {
      data.setScope(TestSearchScope.WHOLE_PROJECT);
    }
    else if (mySingleModuleScope.isSelected()) {
      data.setScope(TestSearchScope.SINGLE_MODULE);
    }
    else if (myModuleWDScope.isSelected()) {
      data.setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    }
    configuration.setAlternativeJrePath(myAlternativeJREPanel.getPath());
    configuration.setAlternativeJrePathEnabled(myAlternativeJREPanel.isPathEnabled());

    myCommonJavaParameters.applyTo(configuration);
  }

  public void resetEditorFrom(final JUnitConfiguration configuration) {
    myModel.reset(configuration);
    myCommonJavaParameters.reset(configuration);
    getModuleSelector().reset(configuration);
    final TestSearchScope scope = configuration.getPersistentData().getScope();
    if (scope == TestSearchScope.SINGLE_MODULE) {
      mySingleModuleScope.setSelected(true);
    }
    else if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES) {
      myModuleWDScope.setSelected(true);
    }
    else {
      myWholeProjectScope.setSelected(true);
    }
    myAlternativeJREPanel.init(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());

  }

  private void changePanel () {
    if (myAllInPackageButton.isSelected()) {
      myPackagePanel.setVisible(true);
      myPackage.setVisible(true);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myMethod.setVisible(false);
    }
    else if (myClassButton.isSelected()){
      myPackagePanel.setVisible(false);
      myClass.setVisible(true);
      myMethod.setVisible(false);
    }
    else if (myTestMethodButton.isSelected()){
      myPackagePanel.setVisible(false);
      myClass.setVisible(true);
      myMethod.setVisible(true);
    } else {
      myPackagePanel.setVisible(true);
      myPattern.setVisible(true);
      myPackage.setVisible(false);
      myClass.setVisible(false);
      myMethod.setVisible(false);
    }
  }


  public JComboBox getModulesComponent() { return myModule.getComponent(); }
  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  private void installDocuments() {
    for (int i = 0; i < myTestLocations.length; i++) {
      final LabeledComponent testLocation = getTestLocation(i);
      final JComponent component = testLocation.getComponent();
      final TextFieldWithBrowseButton field;
      if (component instanceof TextFieldWithBrowseButton) {
        field = (TextFieldWithBrowseButton)component;
      } else {
        field = myPatternTextField;
      }
      final Document document = myModel.getJUnitDocument(i);
      field.getTextField().setDocument(document);
      myBrowsers[i].setField(field);
    }
  }

  public LabeledComponent getTestLocation(final int index) {
    return myTestLocations[index];
  }

  private static void addRadioButtonsListeners(final JRadioButton[] radioButtons, ChangeListener listener) {
    final ButtonGroup group = new ButtonGroup();
    for (final JRadioButton radioButton : radioButtons) {
      radioButton.getModel().addChangeListener(listener);
      group.add(radioButton);
    }
    if (group.getSelection() == null) group.setSelected(radioButtons[0].getModel(), true);
  }

  public void onTypeChanged(final int newType) {
    myRadioButtons[newType].setSelected(true);
    final TIntArrayList enabledFields = ourEnabledFields.get(newType);
    for (int i = 0; i < myTestLocations.length; i++)
      getTestLocation(i).setEnabled(enabledFields.contains(i));
    if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE) myModule.setEnabled(true);
    else onScopeChanged();
  }

  private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
    public PackageChooserActionListener(final Project project) {super(project);}

    protected String showDialog() {
      final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject());
      dialog.show();
      final PsiPackage aPackage = dialog.getSelectedPackage();
      return aPackage != null ? aPackage.getQualifiedName() : null;
    }
  }

  private class TestsChooserActionListener extends TestClassBrowser {
    public TestsChooserActionListener(final Project project) {
      super(project);
    }

    @Override
    protected void onClassChoosen(PsiClass psiClass) {
      final JTextField textField = myPatternTextField.getTextField();
      final String text = textField.getText();
      textField.setText(text + (text.length() > 0 ? "||" : "") + psiClass.getQualifiedName());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      showDialog();
    }
  }

  private void onScopeChanged() {
    myModule.setEnabled(!myWholeProjectScope.isSelected());
  }

  private class TestClassBrowser extends ClassBrowser {
    public TestClassBrowser(final Project project) {
      super(project, ExecutionBundle.message("choose.test.class.dialog.title"));
    }

    protected void onClassChoosen(final PsiClass psiClass) {
      setPackage(JUnitUtil.getContainingPackage(psiClass));
    }

    protected PsiClass findClass(final String className) {
      return getModuleSelector().findClass(className);
    }

    protected TreeClassChooser.ClassFilterWithScope getFilter() throws NoFilterException {
      final ConfigurationModuleSelector moduleSelector = getModuleSelector();
      final Module module = moduleSelector.getModule();
      if (module == null) {
        throw NoFilterException.moduleDoesntExist(moduleSelector);
      }
      final TreeClassChooser.ClassFilterWithScope classFilter;
      try {
        final JUnitConfiguration configurationCopy = new JUnitConfiguration(ExecutionBundle.message("default.junit.configuration.name"), getProject(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
        applyEditorTo(configurationCopy);
        classFilter = TestClassFilter.create(configurationCopy.getTestObject().getSourceScope(), configurationCopy.getConfigurationModule().getModule());
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw NoFilterException.noJUnitInModule(module);
      }
      return classFilter;
    }
  }

  private class MethodBrowser extends BrowseModuleValueActionListener {
    public MethodBrowser(final Project project) {
      super(project);
    }

    protected String showDialog() {
      final String className = getClassName();
      if (className.trim().length() == 0) {
        Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"), ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
        return null;
      }
      final PsiClass testClass = getModuleSelector().findClass(className);
      if (testClass == null) {
        Messages.showMessageDialog(getField(), ExecutionBundle.message("class.does.not.exists.error.message", className), ExecutionBundle.message("cannot.browse.method.dialog.title"),
                                   Messages.getInformationIcon());
        return null;
      }
      final MethodListDlg dlg = new MethodListDlg(testClass, new JUnitUtil.TestMethodFilter(testClass), getField());
      dlg.show();
      if (dlg.isOK()) {
        final PsiMethod method = dlg.getSelected();
        if (method != null) {
          return method.getName();
        }
      }
      return null;
    }
  }

  private String getClassName() {
    return ((LabeledComponent<TextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CLASS).getComponent()).getText();
  }

  private void setPackage(final PsiPackage aPackage) {
    if (aPackage == null) return;
    ((LabeledComponent<TextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.ALL_IN_PACKAGE).getComponent()).setText(aPackage.getQualifiedName());
  }

  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  public void disposeEditor() {
  }

  private void applyHelpersTo(final JUnitConfiguration currentState) {
    myCommonJavaParameters.applyTo(currentState);
    getModuleSelector().applyTo(currentState);
  }
}
