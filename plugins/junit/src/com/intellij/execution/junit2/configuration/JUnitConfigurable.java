// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit2.configuration;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
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
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ArrayUtilRt;
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
    new TIntArrayList(new int[]{6}),
    new TIntArrayList(new int[]{1, 2})
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
  private LabeledComponent<JCheckBox> myUseModulePath;
  private CommonJavaParametersPanel myCommonJavaParameters;
  private JRadioButton myWholeProjectScope;
  private JRadioButton mySingleModuleScope;
  private JRadioButton myModuleWDScope;
  private final TextFieldWithBrowseButton myPatternTextField;
  private JrePathEditor myJrePathEditor;
  private LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private JComboBox myForkCb;
  private JBLabel myTestLabel;
  private JComboBox<Integer> myTypeChooser;
  private JBLabel mySearchForTestsLabel;
  private JPanel myScopesPanel;
  private JComboBox<String> myRepeatCb;
  private JTextField myRepeatCountField;
  private LabeledComponent<JComboBox<String>> myChangeListLabeledComponent;
  private LabeledComponent<RawCommandLineEditor> myUniqueIdField;
  private LabeledComponent<RawCommandLineEditor> myTagsField;
  private final Project myProject;
  private JComponent anchor;

  public JUnitConfigurable(final Project project) {
    myProject = project;
    myModel = new JUnitConfigurationModel(project);
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(getModulesComponent(), false));
    myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
    myCommonJavaParameters.setHasModuleMacro();
    myModule.getComponent().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
        reloadTestKindModel();
      }
    });
    myBrowsers = new BrowseModuleValueActionListener[]{
      new PackageChooserActionListener(project),
      new TestClassBrowser(project),
      new MethodBrowser(project) {
        @Override
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
      new CategoryBrowser(project),
      null
    };

    reloadTestKindModel();
    myTypeChooser.setRenderer(SimpleListCellRenderer.create("", value -> {
      switch (value) {
        case JUnitConfigurationModel.ALL_IN_PACKAGE:
          return "All in package";
        case JUnitConfigurationModel.DIR:
          return "All in directory";
        case JUnitConfigurationModel.PATTERN:
          return "Pattern";
        case JUnitConfigurationModel.CLASS:
          return "Class";
        case JUnitConfigurationModel.METHOD:
          return "Method";
        case JUnitConfigurationModel.CATEGORY:
          return "Category";
        case JUnitConfigurationModel.UNIQUE_ID:
          return "UniqueId";
        case JUnitConfigurationModel.TAGS:
          return "Tags";
        case JUnitConfigurationModel.BY_SOURCE_POSITION:
          return "Through source location";
        case JUnitConfigurationModel.BY_SOURCE_CHANGES:
          return "Over changes in sources";
      }
      throw new IllegalArgumentException(String.valueOf(value));
    }));

    myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
    myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
    myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
    myTestLocations[JUnitConfigurationModel.DIR] = myDir;
    myTestLocations[JUnitConfigurationModel.CATEGORY] = myCategory;

    myRepeatCb.setModel(new DefaultComboBoxModel<>(RepeatCount.REPEAT_TYPES));
    myRepeatCb.setSelectedItem(RepeatCount.ONCE);
    myRepeatCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRepeatCountField.setEnabled(RepeatCount.N.equals(myRepeatCb.getSelectedItem()));
      }
    });

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton(new ExpandableTextField(text -> Arrays.asList(text.split("\\|\\|")),
                                                                               strings -> StringUtil.join(strings, "||")));
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
      @Override
      public void stateChanged(final ChangeEvent e) {
        onScopeChanged();
      }
    });

    UIUtil.setEnabled(myCommonJavaParameters.getProgramParametersComponent(), false, true);

    setAnchor(mySearchForTestsLabel);
    myJrePathEditor.setAnchor(myModule.getLabel());
    myUseModulePath.setAnchor(myModule.getLabel());
    myCommonJavaParameters.setAnchor(myModule.getLabel());
    myShortenClasspathModeCombo.setAnchor(myModule.getLabel());

    final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    myChangeListLabeledComponent.getComponent().setModel(model);
    model.addElement(JUnitBundle.message("test.discovery.by.all.changes.combo.item"));

    if (!project.isDefault()) {
      final List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
      for (LocalChangeList changeList : changeLists) {
        model.addElement(changeList.getName());
      }
    }

    myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent()));

    myUseModulePath.getComponent().setText(ExecutionBundle.message("use.module.path.checkbox.label"));
    myUseModulePath.getComponent().setSelected(true);
    myUseModulePath.setVisible(FilenameIndex.getFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, GlobalSearchScope.projectScope(myProject)).length > 0);
  }

  private void reloadTestKindModel() {
    int selectedIndex = myTypeChooser.getSelectedIndex();
    final DefaultComboBoxModel<Integer> aModel = new DefaultComboBoxModel<>();
    aModel.addElement(JUnitConfigurationModel.ALL_IN_PACKAGE);
    aModel.addElement(JUnitConfigurationModel.DIR);
    aModel.addElement(JUnitConfigurationModel.PATTERN);
    aModel.addElement(JUnitConfigurationModel.CLASS);
    aModel.addElement(JUnitConfigurationModel.METHOD);

    Module module = getModuleSelector().getModule();
    GlobalSearchScope searchScope = module != null ? GlobalSearchScope.moduleRuntimeScope(module, true)
                                                   : GlobalSearchScope.allScope(myProject);

    if (myProject.isDefault() ||
        JavaPsiFacade.getInstance(myProject).findPackage("org.junit") != null) {
      aModel.addElement(JUnitConfigurationModel.CATEGORY);
    }

    if (myProject.isDefault() ||
        JUnitUtil.isJUnit5(searchScope, myProject) ||
        TestObject.hasJUnit5EnginesAPI(searchScope, JavaPsiFacade.getInstance(myProject))) {
      aModel.addElement(JUnitConfigurationModel.UNIQUE_ID);
      aModel.addElement(JUnitConfigurationModel.TAGS);
    }

    if (Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY)) {
      aModel.addElement(JUnitConfigurationModel.BY_SOURCE_POSITION);
      aModel.addElement(JUnitConfigurationModel.BY_SOURCE_CHANGES);
    }
    myTypeChooser.setModel(aModel);
    myTypeChooser.setSelectedIndex(selectedIndex);
  }

  private static void addRadioButtonsListeners(final JRadioButton[] radioButtons, ChangeListener listener) {
    final ButtonGroup group = new ButtonGroup();
    for (final JRadioButton radioButton : radioButtons) {
      radioButton.getModel().addChangeListener(listener);
      group.add(radioButton);
    }
    if (group.getSelection() == null) group.setSelected(radioButtons[0].getModel(), true);
  }

  @Override
  public void applyEditorTo(@NotNull final JUnitConfiguration configuration) {
    configuration.setRepeatMode((String)myRepeatCb.getSelectedItem());
    try {
      configuration.setRepeatCount(Integer.parseInt(myRepeatCountField.getText()));
    }
    catch (NumberFormatException e) {
      configuration.setRepeatCount(1);
    }
    configuration.getPersistentData().setUniqueIds(setArrayFromText(myUniqueIdField));
    configuration.getPersistentData().setTags(myTagsField.getComponent().getText());
    configuration.getPersistentData().setChangeList((String)myChangeListLabeledComponent.getComponent().getSelectedItem());
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
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());

    myCommonJavaParameters.applyTo(configuration);
    configuration.setForkMode((String)myForkCb.getSelectedItem());
    configuration.setShortenCommandLine(myShortenClasspathModeCombo.getComponent().getSelectedItem());

    configuration.setUseModulePath(myUseModulePath.isVisible() && myUseModulePath.getComponent().isSelected());
  }

  protected String[] setArrayFromText(LabeledComponent<RawCommandLineEditor> field) {
    String text = field.getComponent().getText();
    if (text.isEmpty()) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    return text.split(" ");
  }

  @Override
  public void resetEditorFrom(@NotNull final JUnitConfiguration configuration) {
    final int count = configuration.getRepeatCount();
    myRepeatCountField.setText(String.valueOf(count));
    myRepeatCountField.setEnabled(count > 1);
    myRepeatCb.setSelectedItem(configuration.getRepeatMode());

    myModel.reset(configuration);
    myChangeListLabeledComponent.getComponent().setSelectedItem(configuration.getPersistentData().getChangeList());
    String[] ids = configuration.getPersistentData().getUniqueIds();
    myUniqueIdField.getComponent().setText(ids != null ? StringUtil.join(ids, " ") : null);

    myTagsField.getComponent().setText(configuration.getPersistentData().getTags());

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
    myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    myUseModulePath.getComponent().setSelected(configuration.isUseModulePath());
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
      myMethod.setVisible(false);
      myChangeListLabeledComponent.setVisible(true);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
    else if (selectedType == JUnitConfigurationModel.UNIQUE_ID) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(false);
      myDir.setVisible(false);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myUniqueIdField.setVisible(true);
      myTagsField.setVisible(false);
      myMethod.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
      myForkCb.setEnabled(true);
      myForkCb.setModel(new DefaultComboBoxModel(FORK_MODE_ALL));
      myForkCb.setSelectedItem(selectedItem);
    }
    else if (selectedType == JUnitConfigurationModel.TAGS) {
      myPackagePanel.setVisible(false);
      myScopesPanel.setVisible(true);
      myDir.setVisible(false);
      myPattern.setVisible(false);
      myClass.setVisible(false);
      myCategory.setVisible(false);
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(true);
      myMethod.setVisible(false);
      myChangeListLabeledComponent.setVisible(false);
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
      myUniqueIdField.setVisible(false);
      myTagsField.setVisible(false);
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
      final ComponentWithBrowseButton<? extends JComponent> field;
      Object document;
      if (component instanceof TextFieldWithBrowseButton) {
        field = (TextFieldWithBrowseButton)component;
        document = new PlainDocument();
        ((TextFieldWithBrowseButton)field).getTextField().setDocument((Document)document);
      }
      else if (component instanceof EditorTextFieldWithBrowseButton) {
        field = (EditorTextFieldWithBrowseButton)component;
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

    myShortenClasspathModeCombo = new LabeledComponent<>();
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
    myUniqueIdField.setAnchor(anchor);
    myTagsField.setAnchor(anchor);
    myChangeListLabeledComponent.setAnchor(anchor);
  }

  public void onTypeChanged(final int newType) {
    myTypeChooser.setSelectedItem(newType);
    final TIntArrayList enabledFields = ourEnabledFields.size() > newType ? ourEnabledFields.get(newType) : null;
    for (int i = 0; i < myTestLocations.length; i++)
      getTestLocation(i).setEnabled(enabledFields != null && enabledFields.contains(i));
    /*if (newType == JUnitConfigurationModel.PATTERN) {
      myModule.setEnabled(false);
    } else */if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE &&
                 newType != JUnitConfigurationModel.PATTERN &&
                 newType != JUnitConfigurationModel.CATEGORY &&
                 newType != JUnitConfigurationModel.TAGS &&
                 newType != JUnitConfigurationModel.UNIQUE_ID) {
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
                                              selectedItem == JUnitConfigurationModel.CATEGORY ||
                                              selectedItem == JUnitConfigurationModel.TAGS ||
                                              selectedItem == JUnitConfigurationModel.UNIQUE_ID ) && myWholeProjectScope.isSelected();
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

  @Override
  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void applyHelpersTo(final JUnitConfiguration currentState) {
    myCommonJavaParameters.applyTo(currentState);
    getModuleSelector().applyTo(currentState);
  }

  private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
    PackageChooserActionListener(final Project project) {
      super(project);
    }

    @Override
    protected String showDialog() {
      final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject());
      dialog.show();
      final PsiPackage aPackage = dialog.getSelectedPackage();
      return aPackage != null ? aPackage.getQualifiedName() : null;
    }
  }

  private class TestsChooserActionListener extends TestClassBrowser {
    TestsChooserActionListener(final Project project) {
      super(project);
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      final JTextField textField = myPatternTextField.getTextField();
      final String text = textField.getText();
      textField.setText(text + (text.length() > 0 ? "||" : "") + psiClass.getQualifiedName());
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      try {
        return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new NoFilterException(new MessagesEx.MessageInfo(getProject(),
                                                               e.getMessage(),
                                                               JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      showDialog();
    }
  }

  private class TestClassBrowser extends ClassBrowser {
    TestClassBrowser(final Project project) {
      super(project, ExecutionBundle.message("choose.test.class.dialog.title"));
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      setPackage(JUnitUtil.getContainingPackage(psiClass));
    }

    @Override
    protected PsiClass findClass(final String className) {
      return getModuleSelector().findClass(className);
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final ConfigurationModuleSelector moduleSelector = getModuleSelector();
      final Module module = moduleSelector.getModule();
      if (module == null) {
        final Project project = moduleSelector.getProject();
        final String moduleName = moduleSelector.getModuleName();
        throw new NoFilterException(new MessagesEx.MessageInfo(
          project,
          moduleName.isEmpty() ? "No module selected" : JUnitBundle.message("module.does.not.exists", moduleName, project.getName()),
          JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
      final ClassFilter.ClassFilterWithScope classFilter;
      try {
        final JUnitConfiguration configurationCopy = new JUnitConfiguration(JUnitBundle.message("default.junit.configuration.name"), getProject());
        applyEditorTo(configurationCopy);
        SourceScope sourceScope = SourceScope.modulesWithDependencies(configurationCopy.getModules());
        GlobalSearchScope globalSearchScope = sourceScope.getGlobalSearchScope();
        if (JUnitUtil.isJUnit5(globalSearchScope, getProject())) {
          return new ClassFilter.ClassFilterWithScope() {
            @Override
            public GlobalSearchScope getScope() {
              return globalSearchScope;
            }

            @Override
            public boolean isAccepted(PsiClass aClass) {
              return JUnitUtil.isTestClass(aClass,true, true);
            }
          };
        }
        classFilter = TestClassFilter.create(sourceScope, configurationCopy.getConfigurationModule().getModule());
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new NoFilterException(new MessagesEx.MessageInfo(
          module.getProject(),
          JUnitBundle.message("junit.not.found.in.module.error.message", module.getName()),
          JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
      return classFilter;
    }
  }

  private class CategoryBrowser extends ClassBrowser {
    CategoryBrowser(Project project) {
      super(project, "Category Interface");
    }

    @Override
    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    @Override
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
        @Override
        public GlobalSearchScope getScope() {
          return scope;
        }

        @Override
        public boolean isAccepted(final PsiClass aClass) {
          return true;
        }
      };
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CATEGORY)).getComponent()
        .setText(psiClass.getQualifiedName());
    }
  }
}