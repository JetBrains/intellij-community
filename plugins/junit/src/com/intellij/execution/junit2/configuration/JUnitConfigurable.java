// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit2.configuration;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.ui.LabeledComponentNoThrow;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
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
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @deprecated Use {@link JUnitSettingsEditor} instead. See IDEA-377975.
 */
@Deprecated(forRemoval = true)
public class JUnitConfigurable<T extends JUnitConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  private static final List<IntArrayList> ourEnabledFields = Arrays.asList(
    new IntArrayList(new int[]{0}),
    new IntArrayList(new int[]{1}),
    new IntArrayList(new int[]{1, 2}),
    new IntArrayList(new int[]{3}),
    new IntArrayList(new int[]{4}),
    new IntArrayList(new int[]{5}),
    new IntArrayList(new int[]{1, 2}),
    new IntArrayList(new int[]{6}),
    new IntArrayList(new int[]{1, 2})
    );
  private static final String[] FORK_MODE_ALL =
    {JUnitConfiguration.FORK_NONE, JUnitConfiguration.FORK_METHOD, JUnitConfiguration.FORK_KLASS};
  private static final String[] FORK_MODE = {JUnitConfiguration.FORK_NONE, JUnitConfiguration.FORK_METHOD};
  private static final String[] FORK_MODE_NONE = {JUnitConfiguration.FORK_NONE};
  private final ConfigurationModuleSelector myModuleSelector;
  private final LabeledComponent[] myTestLocations = new LabeledComponent[6];
  private final JUnitConfigurationModel myModel;
  private final BrowseModuleValueActionListener[] myBrowsers;
  private JComponent myPackagePanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
  private LabeledComponentNoThrow<TextFieldWithBrowseButton> myDir;
  private LabeledComponentNoThrow<JPanel> myPattern;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMethod;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myCategory;
  // Fields
  private JPanel myWholePanel;
  private LabeledComponentNoThrow<ModuleDescriptionsComboBox> myModule;
  private LabeledComponentNoThrow<JCheckBox> myUseModulePath;
  private CommonJavaParametersPanel myCommonJavaParameters;
  private JRadioButton myWholeProjectScope;
  private JRadioButton mySingleModuleScope;
  private JRadioButton myModuleWDScope;
  private final TextFieldWithBrowseButton myPatternTextField;
  private JrePathEditor myJrePathEditor;
  private LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private JComboBox<String> myForkCb;
  private JBLabel myTestLabel;
  private JComboBox<Integer> myTypeChooser;
  private JBLabel mySearchForTestsLabel;
  private JPanel myScopesPanel;
  private JComboBox<String> myRepeatCb;
  private JTextField myRepeatCountField;
  private LabeledComponentNoThrow<JComboBox<String>> myChangeListLabeledComponent;
  private LabeledComponentNoThrow<RawCommandLineEditor> myUniqueIdField;
  private LabeledComponentNoThrow<RawCommandLineEditor> myTagsField;
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
        myModel.reloadTestKindModel(JUnitConfigurable.this.myTypeChooser, myModuleSelector.getModule(), null);
      }
    });
    final TestClassBrowser classBrowser = new TestClassBrowser(myProject, myModuleSelector, myPackage.getComponent());
    myClass.setComponent(new EditorTextFieldWithBrowseButton(myProject, true, createClassVisibilityChecker(classBrowser)));

    myModel.reloadTestKindModel(myTypeChooser, myModuleSelector.getModule(), () -> addListeners());
    myTypeChooser.setRenderer(SimpleListCellRenderer.create("", value -> JUnitConfigurationModel.getKindName(value)));

    myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] = myPackage;
    myTestLocations[JUnitConfigurationModel.CLASS] = myClass;
    myTestLocations[JUnitConfigurationModel.METHOD] = myMethod;
    myTestLocations[JUnitConfigurationModel.DIR] = myDir;
    myTestLocations[JUnitConfigurationModel.CATEGORY] = myCategory;

    myRepeatCb.setModel(new DefaultComboBoxModel<>(RepeatCount.REPEAT_TYPES));

    //noinspection HardCodedStringLiteral
    myRepeatCb.setSelectedItem(RepeatCount.ONCE);
    myRepeatCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRepeatCountField.setEnabled(RepeatCount.N.equals(myRepeatCb.getSelectedItem()));
      }
    });

    myRepeatCb.setRenderer(SimpleListCellRenderer.create("", value -> JUnitConfigurationModel.getRepeatModeName(value)));
    myForkCb.setRenderer(SimpleListCellRenderer.create("", value -> JUnitConfigurationModel.getForkModeName(value)));

    final JPanel panel = myPattern.getComponent();
    panel.setLayout(new BorderLayout());
    myPatternTextField = new TextFieldWithBrowseButton(new ExpandableTextField(text -> Arrays.asList(text.split("\\|\\|")),
                                                                               strings -> StringUtil.join(strings, "||")));
    myPatternTextField.setButtonIcon(AllIcons.General.Add);
    panel.add(myPatternTextField, BorderLayout.CENTER);
    myTestLocations[JUnitConfigurationModel.PATTERN] = myPattern;

    final FileChooserDescriptor dirFileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    dirFileChooser.setHideIgnored(false);
    final JTextField textField = myDir.getComponent().getTextField();
    InsertPathAction.addTo(textField, dirFileChooser);
    FileChooserFactory.getInstance().installFileCompletion(textField, dirFileChooser, true, null);
    // Done

    myBrowsers = createBrowsers(project, myModuleSelector, myPackage.getComponent(), myPatternTextField, myCategory.getComponent(), () -> getClassName());
    myModel.setListener((oldType, newType) -> onTypeChanged(newType));

    myTypeChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Integer item = (Integer)myTypeChooser.getSelectedItem();
        if (item != null) {
          myModel.setType(item);
          changePanel();
        }
      }
    }
    );

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

    setupChangeLists(project, myChangeListLabeledComponent.getComponent());

    myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent()) {
      @Override
      protected boolean productionOnly() {
        return false;
      }
    });

    myUseModulePath.getComponent().setText(ExecutionBundle.message("use.module.path.checkbox.label"));
    myUseModulePath.getComponent().setSelected(true);
  }

  private void addListeners() {
    myRepeatCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int testType = (Integer)Objects.requireNonNull(myTypeChooser.getSelectedItem());
        if (testType == JUnitConfigurationModel.CLASS || testType == JUnitConfigurationModel.METHOD) {
          String[] model = getForkModel(testType, JUnitConfigurable.this.myRepeatCb.getSelectedItem());
          myForkCb.setModel(new DefaultComboBoxModel<>(model));
        }
      }
    });
  }

  static void setupChangeLists(Project project, JComboBox<String> comboBox) {
    final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    comboBox.setModel(model);
    model.addElement(JUnitBundle.message("test.discovery.by.all.changes.combo.item"));

    if (!project.isDefault()) {
      final List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
      for (LocalChangeList changeList : changeLists) {
        model.addElement(changeList.getName());
      }
    }
  }

  public static BrowseModuleValueActionListener[] createBrowsers(Project project,
                                                                 ConfigurationModuleSelector moduleSelector,
                                                                 EditorTextFieldWithBrowseButton packageField,
                                                                 TextFieldWithBrowseButton patternField,
                                                                 EditorTextFieldWithBrowseButton categoryField,
                                                                 Supplier<String> className) {
    return new BrowseModuleValueActionListener[]{
      new PackageChooserActionListener(project),
      new TestClassBrowser(project, moduleSelector, packageField),
      new MethodBrowser(project) {
        @Override
        protected Condition<PsiMethod> getFilter(PsiClass testClass) {
          return new JUnitUtil.TestMethodFilter(testClass);
        }

        @Override
        protected String getClassName() {
          return className.get();
        }

        @Override
        protected ConfigurationModuleSelector getModuleSelector() {
          return moduleSelector;
        }
      },
      new TestsChooserActionListener(project, moduleSelector, packageField, patternField),
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
      new CategoryBrowser(project, moduleSelector, categoryField),
      null
    };
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
  public void applyEditorTo(final @NotNull JUnitConfiguration configuration) {
    configuration.setRepeatMode((String)myRepeatCb.getSelectedItem());
    try {
      configuration.setRepeatCount(Integer.parseInt(myRepeatCountField.getText()));
    }
    catch (NumberFormatException e) {
      configuration.setRepeatCount(1);
    }
    configuration.getPersistentData().setUniqueIds(setArrayFromText(myUniqueIdField.getComponent().getText()));
    configuration.getPersistentData().setTags(myTagsField.getComponent().getText());
    configuration.getPersistentData().setChangeList((String)myChangeListLabeledComponent.getComponent().getSelectedItem());
    myModel.apply(getModuleSelector().getModule(), configuration, null);
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

  static String[] setArrayFromText(String text) {
    if (text.isEmpty()) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    return text.split("\u001B");
  }

  @Override
  public void resetEditorFrom(final @NotNull JUnitConfiguration configuration) {
    final int count = configuration.getRepeatCount();
    myRepeatCountField.setText(String.valueOf(count));
    myRepeatCountField.setEnabled(count > 1);

    //noinspection HardCodedStringLiteral
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
    //noinspection HardCodedStringLiteral
    myForkCb.setSelectedItem(configuration.getForkMode());
    myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    myUseModulePath.getComponent().setSelected(configuration.isUseModulePath());
    if (!myProject.isDefault()) {
      SwingUtilities.invokeLater(() -> 
         ReadAction.nonBlocking(() -> FilenameIndex.getFilesByName(myProject, PsiJavaModule.MODULE_INFO_FILE, GlobalSearchScope.projectScope(myProject)).length > 0)
                   .expireWith(this)
                   .finishOnUiThread(ModalityState.stateForComponent(myUseModulePath), visible -> myUseModulePath.setVisible(visible))
                   .submit(NonUrgentExecutor.getInstance()));
    }
  }

  private void changePanel () {
    final Integer selectedType = (Integer)myTypeChooser.getSelectedItem();
    if (selectedType == null) return;
    myPackagePanel.setVisible(selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE);
    myScopesPanel.setVisible(selectedType == JUnitConfigurationModel.PATTERN ||
                             selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE ||
                             selectedType == JUnitConfigurationModel.TAGS ||
                             selectedType == JUnitConfigurationModel.CATEGORY);
    myPattern.setVisible(selectedType == JUnitConfigurationModel.PATTERN);
    myDir.setVisible(selectedType == JUnitConfigurationModel.DIR);
    myClass.setVisible(selectedType == JUnitConfigurationModel.CLASS ||
                       selectedType == JUnitConfigurationModel.METHOD ||
                       selectedType == JUnitConfigurationModel.BY_SOURCE_POSITION);
    myMethod.setVisible(selectedType == JUnitConfigurationModel.PATTERN ||
                        selectedType == JUnitConfigurationModel.METHOD ||
                        selectedType == JUnitConfigurationModel.BY_SOURCE_POSITION);
    myCategory.setVisible(selectedType == JUnitConfigurationModel.CATEGORY);
    myUniqueIdField.setVisible(selectedType == JUnitConfigurationModel.UNIQUE_ID);
    myTagsField.setVisible(selectedType == JUnitConfigurationModel.TAGS);
    myChangeListLabeledComponent.setVisible(selectedType == JUnitConfigurationModel.BY_SOURCE_CHANGES);

    myForkCb.setModel(new DefaultComboBoxModel<>(getForkModel(selectedType, myRepeatCb.getSelectedItem())));
    myForkCb.setSelectedItem(updateForkMethod(selectedType, (String)myForkCb.getSelectedItem(), myRepeatCb.getSelectedItem()));
  }

  public static @NotNull String updateForkMethod(Integer selectedType, String forkMethod, Object repeat) {
    if (forkMethod == null) {
      forkMethod = JUnitConfiguration.FORK_NONE;
    }
    else if (selectedType == JUnitConfigurationModel.CLASS && JUnitConfiguration.FORK_KLASS.equals(forkMethod) &&
             RepeatCount.ONCE.equals(repeat)) {
      forkMethod = JUnitConfiguration.FORK_METHOD;
    }
    return forkMethod;
  }

  public static String[] getForkModel(int selectedType, Object repeat) {
    if (selectedType != JUnitConfigurationModel.CLASS &&
        selectedType != JUnitConfigurationModel.METHOD &&
        selectedType != JUnitConfigurationModel.BY_SOURCE_POSITION) {
      return FORK_MODE_ALL;
    }

    boolean isMethod = selectedType == JUnitConfigurationModel.METHOD ||
                       selectedType == JUnitConfigurationModel.BY_SOURCE_POSITION;
    boolean once = RepeatCount.ONCE.equals(repeat);
    String[] model = FORK_MODE;
    if (once && isMethod) {
      model = FORK_MODE_NONE;
    }
    else if (!once && !isMethod) {
      model = FORK_MODE_ALL;
    }
    return model;
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
    myMethod.setComponent(new EditorTextFieldWithBrowseButton(myProject, true,
                                                              JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
                                                              PlainTextLanguage.INSTANCE.getAssociatedFileType()));

    myShortenClasspathModeCombo = new LabeledComponent<>();
  }

  public static @NotNull JavaCodeFragment.VisibilityChecker createClassVisibilityChecker(TestClassBrowser classBrowser) {
    return new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        try {
          if (declaration instanceof PsiClass &&
              (classBrowser.getFilter().isAccepted(((PsiClass)declaration)) ||
               classBrowser.findClass(((PsiClass)declaration).getQualifiedName()) != null && place.getParent() != null)) {
            return Visibility.VISIBLE;
          }
        }
        catch (ClassBrowser.NoFilterException e) {
          return Visibility.NOT_VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    };
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
    final IntList enabledFields = ourEnabledFields.size() > newType ? ourEnabledFields.get(newType) : null;
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
    final boolean allInPackageAllInProject = myModel.disableModuleClasspath(myWholeProjectScope.isSelected());
    myModule.setEnabled(!allInPackageAllInProject);
    if (allInPackageAllInProject) {
      myModule.getComponent().setSelectedItem(null);
    }
  }

  private String getClassName() {
    return ((LabeledComponent<EditorTextFieldWithBrowseButton>)getTestLocation(JUnitConfigurationModel.CLASS)).getComponent().getText();
  }

  @Override
  public @NotNull JComponent createEditor() {
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

  private static class TestsChooserActionListener extends TestClassBrowser {
    private final TextFieldWithBrowseButton myPatternTextField;

    TestsChooserActionListener(final Project project, ConfigurationModuleSelector moduleSelector,
                               EditorTextFieldWithBrowseButton packageField, TextFieldWithBrowseButton patternField) {
      super(project, moduleSelector, packageField);
      myPatternTextField = patternField;
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      final JTextField textField = myPatternTextField.getTextField();
      final String text = textField.getText();
      textField.setText(text + (!text.isEmpty() ? "||" : "") + psiClass.getQualifiedName());
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
        try {
          return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
        }
        catch (JUnitUtil.NoJUnitException e) {
          throw new NoFilterException(new MessagesEx.MessageInfo(getProject(),
                                                                 e.getMessage(),
                                                                 JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
        }
      });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      showDialog();
    }
  }

  public static class TestClassBrowser extends ClassBrowser {
    private final ConfigurationModuleSelector myModuleSelector;
    private final EditorTextFieldWithBrowseButton myPackageTextField;

    TestClassBrowser(final Project project, ConfigurationModuleSelector moduleSelector, EditorTextFieldWithBrowseButton packageTextField) {
      super(project, ExecutionBundle.message("choose.test.class.dialog.title"));
      myModuleSelector = moduleSelector;
      myPackageTextField = packageTextField;
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      myPackageTextField.setText(StringUtil.getPackageName(Objects.requireNonNull(psiClass.getQualifiedName())));
    }

    @Override
    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      if (module == null) {
        final Project project = myModuleSelector.getProject();
        final String moduleName = myModuleSelector.getModuleName();
        throw new NoFilterException(new MessagesEx.MessageInfo(
          project,
          moduleName.isEmpty() ? JUnitBundle.message("no.module.selected.error.message")
                               : JUnitBundle.message("module.does.not.exists", moduleName, project.getName()),
          JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
      final ClassFilter.ClassFilterWithScope classFilter;
      try {
        final JUnitConfiguration configurationCopy = new JUnitConfiguration(JUnitBundle.message("default.junit.configuration.name"), getProject());
        myModuleSelector.applyTo(configurationCopy);
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
        classFilter = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
          () -> TestClassFilter.create(sourceScope, configurationCopy.getConfigurationModule().getModule()));
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

  private static class CategoryBrowser extends ClassBrowser {
    private final ConfigurationModuleSelector myModuleSelector;
    private final EditorTextFieldWithBrowseButton myCategoryField;

    CategoryBrowser(Project project, ConfigurationModuleSelector moduleSelector, EditorTextFieldWithBrowseButton categoryField) {
      super(project, JUnitBundle.message("category.interface.dialog.title"));
      myModuleSelector = moduleSelector;
      myCategoryField = categoryField;
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
        scope = GlobalSearchScope.allScope(getProject());
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
      myCategoryField.setText(psiClass.getQualifiedName());
    }
  }
}