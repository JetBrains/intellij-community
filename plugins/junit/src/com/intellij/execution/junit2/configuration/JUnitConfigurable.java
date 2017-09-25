/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.*;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.ui.*;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
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

public class JUnitConfigurable<T extends JUnitConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  private static final List<TIntArrayList> ourEnabledFields = Arrays.asList(
    new TIntArrayList(new int[]{0}),
    new TIntArrayList(new int[]{1}),
    new TIntArrayList(new int[]{1, 2}),
    new TIntArrayList(new int[]{3}),
    new TIntArrayList(new int[]{4}),
    new TIntArrayList(new int[]{5}),
    new TIntArrayList(new int[]{1, 2}),
    new TIntArrayList(new int[]{6})
    );
  private static final String[] FORK_MODE_ALL =
    {JUnitConfiguration.FORK_NONE, JUnitConfiguration.FORK_METHOD, JUnitConfiguration.FORK_KLASS};
  private static final String[] FORK_MODE = {JUnitConfiguration.FORK_NONE, JUnitConfiguration.FORK_METHOD};
  private final ConfigurationModuleSelector myModuleSelector;
  private final LabeledComponent[] myTestLocations = new LabeledComponent[6];
  private final JUnitConfigurationModel myModel;
  private final BrowseModuleValueActionListener[] myBrowsers;
  private JComponent myPackagePanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
  private LabeledComponent<TextFieldWithBrowseButton> myDir;
  private LabeledComponent<JPanel> myPattern;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMethod;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myCategory;
  // Fields
  private JPanel myWholePanel;
  private LabeledComponent<ModuleDescriptionsComboBox> myModule;
  private CommonJavaParametersPanel myCommonJavaParameters;
  private JRadioButton myWholeProjectScope;
  private JRadioButton mySingleModuleScope;
  private JRadioButton myModuleWDScope;
  private TextFieldWithBrowseButton myPatternTextField;
  private JrePathEditor myJrePathEditor;
  private JComboBox myForkCb;
  private JBLabel myTestLabel;
  private JComboBox myTypeChooser;
  private JBLabel mySearchForTestsLabel;
  private JPanel myScopesPanel;
  private JComboBox myRepeatCb;
  private JTextField myRepeatCountField;
  private LabeledComponent<JComboBox<String>> myChangeListLabeledComponent;
  private Project myProject;
  private JComponent anchor;

  public JUnitConfigurable(final Project project) {
    myProject = project;
    myModel = new JUnitConfigurationModel(project);
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(getModulesComponent(), false));
    myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
    myCommonJavaParameters.setHasModuleMacro();
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    myBrowsers = new BrowseModuleValueActionListener[]{
      new PackageChooserActionListener(project),
      new TestClassBrowser(project),
      new MethodBrowser(project) {
        protected Condition<PsiMethod> getFilter(PsiClass testClass) {
          return new JUnitUtil.TestMethodFilter(testClass);
        }

        @Override
        protected String getClassName() {
          return JUnitConfigurable.this.getClassName();
        }

        @Override
        protected ConfigurationModuleSelector getModuleSelector() {
          return myModuleSelector;
        }
      },
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
      },
      new CategoryBrowser(project)
    };
    // Garbage support
    final DefaultComboBoxModel aModel = new DefaultComboBoxModel();
    aModel.addElement(JUnitConfigurationModel.ALL_IN_PACKAGE);
    aModel.addElement(JUnitConfigurationModel.DIR);
    aModel.addElement(JUnitConfigurationModel.PATTERN);
    aModel.addElement(JUnitConfigurationModel.CLASS);
    aModel.addElement(JUnitConfigurationModel.METHOD);
    aModel.addElement(JUnitConfigurationModel.CATEGORY);
    if (Registry.is("testDiscovery.enabled")) {
      aModel.addElement(JUnitConfigurationModel.BY_SOURCE_POSITION);
      aModel.addElement(JUnitConfigurationModel.BY_SOURCE_CHANGES);
    }
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
          case JUnitConfigurationModel.CATEGORY:
            setText("Category");
            break;
          case JUnitConfigurationModel.BY_SOURCE_POSITION:
            setText("Through source location");
            break;
          case JUnitConfigurationModel.BY_SOURCE_CHANGES:
            setText("Over changes in sources");
            break;
        }
      }
    });

    myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
    myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
    myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
    myTestLocations[JUnitConfigurationModel.DIR] = myDir;
    myTestLocations[JUnitConfigurationModel.CATEGORY] = myCategory;

    myRepeatCb.setModel(new DefaultComboBoxModel(RepeatCount.REPEAT_TYPES));
    myRepeatCb.setSelectedItem(RepeatCount.ONCE);
    myRepeatCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRepeatCountField.setEnabled(RepeatCount.N.equals(myRepeatCb.getSelectedItem()));
      }
    });

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton(new ExpandableTextField());
    myPatternTextField.setButtonIcon(IconUtil.getAddIcon());
    panel.add(myPatternTextField, BorderLayout.CENTER);
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

    myRepeatCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if ((Integer) myTypeChooser.getSelectedItem() == JUnitConfigurationModel.CLASS) {
          myForkCb.setModel(getForkModelBasedOnRepeat());
        }
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

    UIUtil.setEnabled(myCommonJavaParameters.getProgramParametersComponent(), false, true);

    setAnchor(mySearchForTestsLabel);
    myJrePathEditor.setAnchor(myModule.getLabel());
    myCommonJavaParameters.setAnchor(myModule.getLabel());

    final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    myChangeListLabeledComponent.getComponent().setModel(model);
    model.addElement("All");

    final List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
    for (LocalChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
  }

  private static void addRadioButtonsListeners(final JRadioButton[] radioButtons, ChangeListener listener) {
    final ButtonGroup group = new ButtonGroup();
    for (final JRadioButton radioButton : radioButtons) {
      radioButton.getModel().addChangeListener(listener);
      group.add(radioButton);
    }
    if (group.getSelection() == null) group.setSelected(radioButtons[0].getModel(), true);
  }

  public void applyEditorTo(@NotNull final JUnitConfiguration configuration) {
    configuration.setRepeatMode((String)myRepeatCb.getSelectedItem());
    try {
      configuration.setRepeatCount(Integer.parseInt(myRepeatCountField.getText()));
    }
    catch (NumberFormatException e) {
      configuration.setRepeatCount(1);
    }
    myModel.apply(getModuleSelector().getModule(), configuration);
    configuration.getPersistentData().setChangeList((String)myChangeListLabeledComponent.getComponent().getSelectedItem());
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
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());

    myCommonJavaParameters.applyTo(configuration);
    configuration.setForkMode((String)myForkCb.getSelectedItem());
  }

  public void resetEditorFrom(@NotNull final JUnitConfiguration configuration) {
    final int count = configuration.getRepeatCount();
    myRepeatCountField.setText(String.valueOf(count));
    myRepeatCountField.setEnabled(count > 1);
    myRepeatCb.setSelectedItem(configuration.getRepeatMode());

    myModel.reset(configuration);
    myChangeListLabeledComponent.getComponent().setSelectedItem(configuration.getPersistentData().getChangeList());
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
    myJrePathEditor
      .setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    myForkCb.setSelectedItem(configuration.getForkMode());
  }

  private void changePanel () {
    String selectedItem = (String)myForkCb.getSelectedItem();
    if (selectedItem == null) {
      selectedItem = JUnitConfiguration.FORK_NONE;
    }
    final Integer selectedType = (Integer)myTypeChooser.getSelectedItem();
    if (selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE) {
      myPackagePanel.setVisible(true);
      myScopesPanel.setVisible(true);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myMethod.setVisible(false);
      myDir.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    } else if (selectedType == JUnitConfigurationModel.DIR) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myDir.setVisible(true);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
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
      myCategory.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
      myMethod.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(getForkModelBasedOnRepeat());
      myForkCb.setSelectedItem(selectedItem != JUnitConfiguration.FORK_KLASS ? selectedItem : JUnitConfiguration.FORK_METHOD);
    }
    else if (selectedType == JUnitConfigurationModel.METHOD || selectedType == JUnitConfigurationModel.BY_SOURCE_POSITION){
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myPattern.setVisible(false);
      myDir.setVisible(false);
      myClass.setVisible(true);
      myCategory.setVisible(false);
      myMethod.setVisible(true);
      myChangeListLabeledComponent.setVisible(false);
      myForkCb.setEnabled(false);
      myForkCb.setSelectedItem(JUnitConfiguration.FORK_NONE);
    } else if (selectedType == JUnitConfigurationModel.CATEGORY) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(true);
      myDir.setVisible(false);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(true);
      myMethod.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
    else if (selectedType == JUnitConfigurationModel.BY_SOURCE_CHANGES) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myDir.setVisible(false);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myMethod.setVisible(false);
      myChangeListLabeledComponent.setVisible(true);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
    else {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(true);
      myPattern.setVisible(true);
      myDir.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myMethod.setVisible(true);
      myChangeListLabeledComponent.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
  }

  private DefaultComboBoxModel getForkModelBasedOnRepeat() {
    return new DefaultComboBoxModel(RepeatCount.ONCE.equals(myRepeatCb.getSelectedItem()) ? FORK_MODE : FORK_MODE_ALL);
  }

  public ModuleDescriptionsComboBox getModulesComponent() {
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
      Object document;
      if (component instanceof TextFieldWithBrowseButton) {
        field = (TextFieldWithBrowseButton)component;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
      } else if (component instanceof EditorTextFieldWithBrowseButton) {
        field = (ComponentWithBrowseButton)component;
        document = ((EditorTextField)field.getChildComponent()).getDocument();
      }
      else {
        field = myPatternTextField;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
        
      }
      myBrowsers[i].setField(field);
      if (myBrowsers[i] instanceof MethodBrowser) {
        final EditorTextField childComponent = (EditorTextField)field.getChildComponent();
        ((MethodBrowser)myBrowsers[i]).installCompletion(childComponent);
        document = childComponent.getDocument();
      }
      myModel.setJUnitDocument(i, document);
    }
  }

  public LabeledComponent getTestLocation(final int index) {
    return myTestLocations[index];
  }

  private void createUIComponents() {
    myPackage = new LabeledComponent<>();
    myPackage.setComponent(new EditorTextFieldWithBrowseButton(myProject, false));

    myClass = new LabeledComponent<>();
    final TestClassBrowser classBrowser = new TestClassBrowser(myProject);
    myClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        try {
          if (declaration instanceof PsiClass && (classBrowser.getFilter().isAccepted(((PsiClass)declaration)) || classBrowser.findClass(((PsiClass)declaration).getQualifiedName()) != null && place.getParent() != null)) {
            return Visibility.VISIBLE;
          }
        }
        catch (ClassBrowser.NoFilterException e) {
          return Visibility.NOT_VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    }));

    myCategory = new LabeledComponent<>();
    myCategory.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        if (declaration instanceof PsiClass) {
          return Visibility.VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    }));

    myMethod = new LabeledComponent<>();
    final EditorTextFieldWithBrowseButton textFieldWithBrowseButton = new EditorTextFieldWithBrowseButton(myProject, true,
                                                                                                          JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
                                                                                                          PlainTextLanguage.INSTANCE.getAssociatedFileType());
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
    myCategory.setAnchor(anchor);
    myChangeListLabeledComponent.setAnchor(anchor);
  }

  public void onTypeChanged(final int newType) {
    myTypeChooser.setSelectedItem(newType);
    final TIntArrayList enabledFields = ourEnabledFields.get(newType);
    for (int i = 0; i < myTestLocations.length; i++)
      getTestLocation(i).setEnabled(enabledFields.contains(i));
    /*if (newType == JUnitConfigurationModel.PATTERN) {
      myModule.setEnabled(false);
    } else */if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE &&
                 newType != JUnitConfigurationModel.PATTERN &&
                 newType != JUnitConfigurationModel.CATEGORY) {
      myModule.setEnabled(true);
    }
    else {
      onScopeChanged();
    }
  }

  private void onScopeChanged() {
    final Integer selectedItem = (Integer)myTypeChooser.getSelectedItem();
    final boolean allInPackageAllInProject = (selectedItem == JUnitConfigurationModel.ALL_IN_PACKAGE ||
                                              selectedItem == JUnitConfigurationModel.PATTERN ||
                                              selectedItem == JUnitConfigurationModel.CATEGORY) && myWholeProjectScope.isSelected();
    myModule.setEnabled(!allInPackageAllInProject);
    if (allInPackageAllInProject) {
      myModule.getComponent().setSelectedItem(null);
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

  private void applyHelpersTo(final JUnitConfiguration currentState) {
    myCommonJavaParameters.applyTo(currentState);
    getModuleSelector().applyTo(currentState);
  }

  private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
    public PackageChooserActionListener(final Project project) {
      super(project);
    }

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
          .create(SourceScope.modulesWithDependencies(configurationCopy.getModules()), configurationCopy.getConfigurationModule().getModule());
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw NoFilterException.noJUnitInModule(module);
      }
      return classFilter;
    }
  }

  private class CategoryBrowser extends ClassBrowser {
    public CategoryBrowser(Project project) {
      super(project, "Category Interface");
    }

    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      final GlobalSearchScope scope;
      if (module == null) {
        scope = GlobalSearchScope.allScope(myProject);
      }
      else {
        scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      }
      return new ClassFilter.ClassFilterWithScope() {
        public GlobalSearchScope getScope() {
          return scope;
        }

        public boolean isAccepted(final PsiClass aClass) {
          return true;
        }
      };
    }

    @Override
    protected void onClassChoosen(PsiClass psiClass) {
      ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CATEGORY)).getComponent()
        .setText(psiClass.getQualifiedName());
    }
  }
}
