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

package com.intellij.execution.junit2.configuration;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.TextFieldCompletionProvider;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

public class JUnitConfigurable extends SettingsEditor<JUnitConfiguration> implements PanelWithAnchor {
  private static final List<TIntArrayList> ourEnabledFields = Arrays.asList(
    new TIntArrayList(new int[]{0}),
    new TIntArrayList(new int[]{1}),
    new TIntArrayList(new int[]{1, 2}),
    new TIntArrayList(new int[]{3}),
    new TIntArrayList(new int[]{4})
  );

  private JComponent myPackagePanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
  private LabeledComponent<TextFieldWithBrowseButton> myDir;
  private LabeledComponent<JPanel> myPattern;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMethod;

  // Fields
  private JPanel myWholePanel;
  private LabeledComponent<JComboBox> myModule;
  private CommonJavaParametersPanel myCommonJavaParameters;
  private JRadioButton myWholeProjectScope;
  private JRadioButton mySingleModuleScope;
  private JRadioButton myModuleWDScope;
  private TextFieldWithBrowseButton myPatternTextField;

  private final ConfigurationModuleSelector myModuleSelector;
  private final LabeledComponent[] myTestLocations = new LabeledComponent[5];
  private final JUnitConfigurationModel myModel;

  private final BrowseModuleValueActionListener[] myBrowsers;
  private AlternativeJREPanel myAlternativeJREPanel;
  private JComboBox myForkCb;
  private JBLabel myTestLabel;
  private JComboBox myTypeChooser;
  private JBLabel mySearchForTestsLabel;
  private JPanel myScopesPanel;
  @NonNls private static final String NONE = "none";
  @NonNls private static final String METHOD = "method";
  @NonNls private static final String KLASS = "class";
  private static final String[] FORK_MODE_ALL = {NONE, METHOD, KLASS};
  private static final String[] FORK_MODE = {NONE, METHOD};
  private Project myProject;
  private JComponent anchor;

  public JUnitConfigurable(final Project project) {
    myProject = project;
    myModel = new JUnitConfigurationModel(project);
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    myBrowsers = new BrowseModuleValueActionListener[]{
      new PackageChooserActionListener(project),
      new TestClassBrowser(project),
      new MethodBrowser(project),
      new TestsChooserActionListener(project),
      new BrowseModuleValueActionListener(project) {
        @Override
        protected String showDialog() {
          final VirtualFile virtualFile =
            FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
          if (virtualFile != null) {
            return FileUtil.toSystemDependentName(virtualFile.getPath());
          }
          return null;
        }
      }
    };
    // Garbage support
    final DefaultComboBoxModel aModel = new DefaultComboBoxModel();
    aModel.addElement(JUnitConfigurationModel.ALL_IN_PACKAGE);
    aModel.addElement(JUnitConfigurationModel.DIR);
    aModel.addElement(JUnitConfigurationModel.PATTERN);
    aModel.addElement(JUnitConfigurationModel.CLASS);
    aModel.addElement(JUnitConfigurationModel.METHOD);
    myTypeChooser.setModel(aModel);
    myTypeChooser.setRenderer(new ListCellRendererWrapper<Integer>() {
      @Override
      public void customize(JList list, Integer value, int index, boolean selected, boolean hasFocus) {
        switch (value) {
          case JUnitConfigurationModel.ALL_IN_PACKAGE:
            setText("All in package");
            break;
          case JUnitConfigurationModel.DIR:
            setText("All in directory");
            break;
          case JUnitConfigurationModel.PATTERN:
            setText("Pattern");
            break;
          case JUnitConfigurationModel.CLASS:
            setText("Class");
            break;
          case JUnitConfigurationModel.METHOD:
            setText("Method");
            break;
        }
      }
    });

    myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
    myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
    myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
    myTestLocations[JUnitConfigurationModel.DIR] = myDir;

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton();
    myPatternTextField.setButtonIcon(IconUtil.getAddIcon());
    panel.add(myPatternTextField, BorderLayout.CENTER);
    final FixedSizeButton editBtn = new FixedSizeButton();
    editBtn.setIcon(AllIcons.Actions.ShowViewer);
    editBtn.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myPatternTextField.getTextField(), "Configure suite tests", "EditParametersPopupWindow");
      }
    });
    panel.add(editBtn, BorderLayout.EAST);
    myTestLocations[JUnitConfigurationModel.PATTERN] = myPattern;

    final FileChooserDescriptor dirFileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    dirFileChooser.setHideIgnored(false);
    final JTextField textField = myDir.getComponent().getTextField();
    InsertPathAction.addTo(textField, dirFileChooser);
    FileChooserFactory.getInstance().installFileCompletion(textField, dirFileChooser, true, null);
    // Done

    myModel.setListener(this);

    myTypeChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myTypeChooser.getSelectedItem();
        myModel.setType((Integer)selectedItem);
        changePanel();
      }
    }
    );
    myModel.setType(JUnitConfigurationModel.CLASS);
    installDocuments();
    addRadioButtonsListeners(new JRadioButton[]{myWholeProjectScope, mySingleModuleScope, myModuleWDScope}, null);
    myWholeProjectScope.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        onScopeChanged();
      }
    });

    myCommonJavaParameters.getProgramParametersComponent().setVisible(false);

    setAnchor(mySearchForTestsLabel);
    myModule.setAnchor(myAlternativeJREPanel.getCbEnabled());
    myCommonJavaParameters.setAnchor(myAlternativeJREPanel.getCbEnabled());
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
    configuration.setForkMode((String)myForkCb.getSelectedItem());
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
    myForkCb.setSelectedItem(configuration.getForkMode());
  }

  private void changePanel () {
    String selectedItem = (String)myForkCb.getSelectedItem();
    if (selectedItem == null) {
      selectedItem = NONE;
    }
    final Integer selectedType = (Integer)myTypeChooser.getSelectedItem();
    if (selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE) {
      myPackagePanel.setVisible(true);
      myScopesPanel.setVisible(true);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myMethod.setVisible(false);
      myDir.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    } else if (selectedType == JUnitConfigurationModel.DIR) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myDir.setVisible(true);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myMethod.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
    else if (selectedType == JUnitConfigurationModel.CLASS) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myPattern.setVisible(false);
      myDir.setVisible(false);
      myClass.setVisible(true);
      myMethod.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE));
      myForkCb.setSelectedItem(selectedItem != KLASS ? selectedItem : METHOD);
    }
    else if (selectedType == JUnitConfigurationModel.METHOD){
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myPattern.setVisible(false);
      myDir.setVisible(false);
      myClass.setVisible(true);
      myMethod.setVisible(true);
      myForkCb.setEnabled(false);
      myForkCb.setSelectedItem(NONE);
    } else {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(true);
      myPattern.setVisible(true);
      myDir.setVisible(false);
      myClass.setVisible(false);
      myMethod.setVisible(true);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
  }

  public JComboBox getModulesComponent() {
    return myModule.getComponent();
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  private void installDocuments() {
    for (int i = 0; i < myTestLocations.length; i++) {
      final LabeledComponent testLocation = getTestLocation(i);
      final JComponent component = testLocation.getComponent();
      final ComponentWithBrowseButton field;
      final Object document;
      if (component instanceof TextFieldWithBrowseButton) {
        field = (TextFieldWithBrowseButton)component;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
        myModel.setJUnitDocument(i, document);
      } else if (component instanceof EditorTextFieldWithBrowseButton) {
        field = (ComponentWithBrowseButton)component;
        document = ((EditorTextField)field.getChildComponent()).getDocument();
        myModel.setJUnitDocument(i, document);
      }
      else {
        field = myPatternTextField;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
        myModel.setJUnitDocument(i, document);
      }
      myBrowsers[i].setField(field);
    }
  }

  public LabeledComponent getTestLocation(final int index) {
    return myTestLocations[index];
  }

  private void createUIComponents() {
    myPackage = new LabeledComponent<EditorTextFieldWithBrowseButton>();
    myPackage.setComponent(new EditorTextFieldWithBrowseButton(myProject, false));

    myClass = new LabeledComponent<EditorTextFieldWithBrowseButton>();
    final TestClassBrowser classBrowser = new TestClassBrowser(myProject);
    myClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        try {
          if (declaration instanceof PsiClass && classBrowser.getFilter().isAccepted(((PsiClass)declaration))) {
            return Visibility.VISIBLE;
          }
        }
        catch (ClassBrowser.NoFilterException e) {
          return Visibility.NOT_VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    }));

    myMethod = new LabeledComponent<EditorTextFieldWithBrowseButton>();
    final EditorTextFieldWithBrowseButton textFieldWithBrowseButton = new EditorTextFieldWithBrowseButton(myProject, true);
    new TextFieldCompletionProvider() {
      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        final String className = getClassName();
        if (className.trim().length() == 0) {
          return;
        }
        final PsiClass testClass = getModuleSelector().findClass(className);
        if (testClass == null) return;
        final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
        for (PsiMethod psiMethod : testClass.getAllMethods()) {
          if (filter.value(psiMethod)) {
            result.addElement(LookupElementBuilder.create(psiMethod.getName()));
          }
        }
      }
    }.apply(textFieldWithBrowseButton.getChildComponent());
    myMethod.setComponent(textFieldWithBrowseButton);
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    mySearchForTestsLabel.setAnchor(anchor);
    myTestLabel.setAnchor(anchor);
    myClass.setAnchor(anchor);
    myDir.setAnchor(anchor);
    myMethod.setAnchor(anchor);
    myPattern.setAnchor(anchor);
    myPackage.setAnchor(anchor);
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
    myTypeChooser.setSelectedItem(newType);
    final TIntArrayList enabledFields = ourEnabledFields.get(newType);
    for (int i = 0; i < myTestLocations.length; i++)
      getTestLocation(i).setEnabled(enabledFields.contains(i));
    /*if (newType == JUnitConfigurationModel.PATTERN) {
      myModule.setEnabled(false);
    } else */if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE) {
      myModule.setEnabled(true);
    }
    else {
      onScopeChanged();
    }
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
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      try {
        return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
      }
      catch (JUnitUtil.NoJUnitException ignore) {
        throw new NoFilterException(new MessagesEx.MessageInfo(getProject(),
                                                               ignore.getMessage(),
                                                               ExecutionBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      showDialog();
    }
  }

  private void onScopeChanged() {
    myModule.setEnabled(((Integer)myTypeChooser.getSelectedItem()) != JUnitConfigurationModel.ALL_IN_PACKAGE || !myWholeProjectScope.isSelected());
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

    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final ConfigurationModuleSelector moduleSelector = getModuleSelector();
      final Module module = moduleSelector.getModule();
      if (module == null) {
        throw NoFilterException.moduleDoesntExist(moduleSelector);
      }
      final ClassFilter.ClassFilterWithScope classFilter;
      try {
        final JUnitConfiguration configurationCopy =
          new JUnitConfiguration(ExecutionBundle.message("default.junit.configuration.name"), getProject(),
                                 JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
        applyEditorTo(configurationCopy);
        classFilter = TestClassFilter
          .create(configurationCopy.getTestObject().getSourceScope(), configurationCopy.getConfigurationModule().getModule());
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
        Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"),
                                   ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
        return null;
      }
      final PsiClass testClass = getModuleSelector().findClass(className);
      if (testClass == null) {
        Messages.showMessageDialog(getField(), ExecutionBundle.message("class.does.not.exists.error.message", className),
                                   ExecutionBundle.message("cannot.browse.method.dialog.title"),
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
    return ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CLASS)).getComponent().getText();
  }

  private void setPackage(final PsiPackage aPackage) {
    if (aPackage == null) return;
    ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.ALL_IN_PACKAGE)).getComponent()
      .setText(aPackage.getQualifiedName());
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
