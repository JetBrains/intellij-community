/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run.testing;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.configuration.MethodListDlg;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.run.AndroidClassBrowser;
import org.jetbrains.android.run.AndroidClassVisibilityChecker;
import org.jetbrains.android.run.ConfigurationSpecificEditor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static org.jetbrains.android.run.testing.AndroidTestRunConfiguration.*;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 4:17:35 PM
 * To change this template use File | Settings | File Templates.
 */
class TestRunParameters implements ConfigurationSpecificEditor<AndroidTestRunConfiguration> {
  private static final String ANDROID_TEST_BASE_CLASS_NAME = "junit.framework.TestCase";

  private JRadioButton myAllInPackageButton;
  private JRadioButton myClassButton;
  private JRadioButton myTestMethodButton;
  private JRadioButton myAllInModuleButton;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myPackageComponent;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myClassComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myMethodComponent;
  private JPanel myPanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myRunnerComponent;
  private JBLabel myLabelTest;
  private final JRadioButton[] myTestingType2RadioButton = new JRadioButton[4];

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private JComponent anchor;

  TestRunParameters(Project project, ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myPackageComponent.setComponent(new EditorTextFieldWithBrowseButton(project, false));
    bind(myPackageComponent, new MyPackageBrowser());

    myClassComponent.setComponent(new EditorTextFieldWithBrowseButton(project, true,
                                                                      new AndroidClassVisibilityChecker(myProject, moduleSelector,
                                                                                                        ANDROID_TEST_BASE_CLASS_NAME)));
    bind(myClassComponent, new AndroidClassBrowser(project, moduleSelector, ANDROID_TEST_BASE_CLASS_NAME,
                                                   AndroidBundle.message("android.browse.test.class.dialog.title"), false, null));

    myRunnerComponent.setComponent(new EditorTextFieldWithBrowseButton(project, true,
                                                                       new AndroidClassVisibilityChecker(myProject, moduleSelector,
                                                                                                         AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS)));
    bind(myRunnerComponent, new AndroidClassBrowser(project, moduleSelector, AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS,
                                                    AndroidBundle.message("android.browse.instrumentation.class.dialog.title"), true,
                                                    null));
    bind(myMethodComponent, new MyMethodBrowser());

    addTestingType(TEST_ALL_IN_MODULE, myAllInModuleButton);
    addTestingType(TEST_ALL_IN_PACKAGE, myAllInPackageButton);
    addTestingType(TEST_CLASS, myClassButton);
    addTestingType(TEST_METHOD, myTestMethodButton);

    setAnchor(myPackageComponent.getLabel());
  }

  private void addTestingType(final int type, JRadioButton button) {
    myTestingType2RadioButton[type] = button;
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateLabelComponents(type);
      }
    });
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myPackageComponent.setAnchor(anchor);
    myClassComponent.setAnchor(anchor);
    myMethodComponent.setAnchor(anchor);
    myLabelTest.setAnchor(anchor);
  }

  private static void bind(final LabeledComponent<? extends ComponentWithBrowseButton<?>> labeledComponent,
                           BrowseModuleValueActionListener browser) {
    browser.setField(labeledComponent.getComponent());
  }

  private void updateButtonsAndLabelComponents(int type) {
    myAllInModuleButton.setSelected(type == TEST_ALL_IN_MODULE);
    myAllInPackageButton.setSelected(type == TEST_ALL_IN_PACKAGE);
    myClassButton.setSelected(type == TEST_CLASS);
    myTestMethodButton.setSelected(type == TEST_METHOD);
    updateLabelComponents(type);
  }

  private void updateLabelComponents(int type) {
    myPackageComponent.setVisible(type == TEST_ALL_IN_PACKAGE);
    myClassComponent.setVisible(type == TEST_CLASS || type == TEST_METHOD);
    myMethodComponent.setVisible(type == TEST_METHOD);
  }

  private class MyPackageBrowser extends BrowseModuleValueActionListener {
    protected MyPackageBrowser() {
      super(myProject);
    }

    protected String showDialog() {
      Module module = myModuleSelector.getModule();
      if (module == null) {
        Messages.showErrorDialog(myPanel, ExecutionBundle.message("module.not.specified.error.text"));
        return null;
      }
      final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), module);
      dialog.selectPackage(myPackageComponent.getComponent().getText());
      dialog.show();
      final PsiPackage aPackage = dialog.getSelectedPackage();
      return aPackage != null ? aPackage.getQualifiedName() : null;
    }
  }

  private class MyMethodBrowser extends BrowseModuleValueActionListener {
    public MyMethodBrowser() {
      super(myProject);
    }

    protected String showDialog() {
      final String className = myClassComponent.getComponent().getText();
      if (className.trim().length() == 0) {
        Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"),
                                   ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
        return null;
      }
      final PsiClass testClass = myModuleSelector.findClass(className);
      if (testClass == null) {
        Messages.showMessageDialog(getField(), ExecutionBundle.message("class.does.not.exists.error.message", className),
                                   ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
        return null;
      }
      final MethodListDlg dialog = new MethodListDlg(testClass, new JUnitUtil.TestMethodFilter(testClass), getField());
      dialog.show();
      if (dialog.isOK()) {
        final PsiMethod method = dialog.getSelected();
        if (method != null) {
          return method.getName();
        }
      }
      return null;
    }
  }

  private int getTestingType() {
    for (int i = 0, myTestingType2RadioButtonLength = myTestingType2RadioButton.length; i < myTestingType2RadioButtonLength; i++) {
      JRadioButton button = myTestingType2RadioButton[i];
      if (button.isSelected()) {
        return i;
      }
    }
    return -1;
  }

  public void applyTo(AndroidTestRunConfiguration configuration) {
    configuration.TESTING_TYPE = getTestingType();
    configuration.CLASS_NAME = myClassComponent.getComponent().getText();
    configuration.METHOD_NAME = myMethodComponent.getComponent().getText();
    configuration.PACKAGE_NAME = myPackageComponent.getComponent().getText();
    configuration.INSTRUMENTATION_RUNNER_CLASS = myRunnerComponent.getComponent().getText();
  }

  public void resetFrom(AndroidTestRunConfiguration configuration) {
    updateButtonsAndLabelComponents(configuration.TESTING_TYPE);
    myPackageComponent.getComponent().setText(configuration.PACKAGE_NAME);
    myClassComponent.getComponent().setText(configuration.CLASS_NAME);
    myMethodComponent.getComponent().setText(configuration.METHOD_NAME);
    myRunnerComponent.getComponent().setText(configuration.INSTRUMENTATION_RUNNER_CLASS);
  }

  public Component getComponent() {
    return myPanel;
  }
}
