// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.application.ClassEditorField;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestMethod;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.ui.CommandLinePanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.*;

public class JUnitTestKindFragment extends SettingsEditorFragment<JUnitConfiguration, JPanel> {

  private final JUnitConfigurationModel myModel;
  private final ConfigurationModuleSelector myModuleSelector;
  private final ComboBox<Integer> myTypeChooser;
  private final JComponent[] myFields = new JComponent[BY_SOURCE_CHANGES + 1];
  private final Map<JComponent, String> myHints = new HashMap<>();
  private final RawCommandLineEditor myUniqueIdField;
  private final RawCommandLineEditor myTagsField;
  private final ComboBox<String> myChangeLists;

  public JUnitTestKindFragment(Project project, ConfigurationModuleSelector moduleSelector) {
    super("junit.test.kind", null, null, new JPanel(new GridBagLayout()), 90, null, null, configuration -> true);
    myModel = new JUnitConfigurationModel(project);
    myModel.setListener((oldKind, kind) -> kindChanged(oldKind, kind));
    myModuleSelector = moduleSelector;
    myTypeChooser = new ComboBox<>();
    CommandLinePanel.setMinimumWidth(component(), 500);
    component().add(myTypeChooser, new GridBagConstraints());
    myModel.reloadTestKindModel(myTypeChooser, myModuleSelector.getModule());
    myTypeChooser.addActionListener(e -> myModel.setType(myTypeChooser.getItem()));
    myTypeChooser.setRenderer(SimpleListCellRenderer.create("", value -> getKindName(value)));

    EditorTextFieldWithBrowseButton packageField = new EditorTextFieldWithBrowseButton(project, false);
    TextFieldWithBrowseButton pattern = new TextFieldWithBrowseButton(new ExpandableTextField(text -> Arrays.asList(text.split("\\|\\|")),
                                                                                              strings -> StringUtil.join(strings, "||")));
    pattern.setButtonIcon(IconUtil.getAddIcon());
    EditorTextFieldWithBrowseButton category = new EditorTextFieldWithBrowseButton(project, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        if (declaration instanceof PsiClass) {
          return Visibility.VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    });

    TextFieldWithBrowseButton directoryField = new TextFieldWithBrowseButton();
    FileChooserDescriptor dirFileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    dirFileChooser.setHideIgnored(false);
    InsertPathAction.addTo(directoryField.getTextField(), dirFileChooser);
    FileChooserFactory.getInstance().installFileCompletion(directoryField.getTextField(), dirFileChooser, true, null);

    BrowseModuleValueActionListener<?>[] browsers = JUnitConfigurable.createBrowsers(project, moduleSelector, packageField, pattern, category, () -> getClassName());
    JavaCodeFragment.VisibilityChecker classVisibilityChecker = JUnitConfigurable.createClassVisibilityChecker((JUnitConfigurable.TestClassBrowser)browsers[CLASS]);
    EditorTextField classField = ClassEditorField.createClassField(project, () -> moduleSelector.getModule(), classVisibilityChecker, browsers[CLASS]);
    EditorTextFieldWithBrowseButton methodField = new EditorTextFieldWithBrowseButton(project, true,
                                                                                      JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
                                                                                      PlainTextLanguage.INSTANCE.getAssociatedFileType());

    myHints.put(myTypeChooser, JUnitBundle.message("test.kind.hint"));
    setupField(ALL_IN_PACKAGE, packageField, packageField.getChildComponent().getDocument(), browsers[ALL_IN_PACKAGE],
               JUnitBundle.message("test.package.hint"));
    setupField(CLASS, classField, classField.getDocument(), null, JUnitBundle.message("test.class.hint"));
    ((MethodBrowser)browsers[METHOD]).installCompletion(methodField.getChildComponent());
    setupField(METHOD, methodField, methodField.getChildComponent().getDocument(), browsers[METHOD],
               JUnitBundle.message("test.method.hint"));
    setupField(PATTERN, pattern, pattern.getTextField().getDocument(), browsers[PATTERN], JUnitBundle.message("test.pattern.hint"));
    setupField(DIR, directoryField, directoryField.getTextField().getDocument(), browsers[DIR], null);
    setupField(CATEGORY, category, category.getChildComponent().getDocument(), browsers[CATEGORY], null);

    myUniqueIdField = new RawCommandLineEditor();
    setupField(UNIQUE_ID, myUniqueIdField, null, null, null);

    myTagsField = new RawCommandLineEditor();
    setupField(TAGS, myTagsField, null, null, null);

    myChangeLists = new ComboBox<>();
    JUnitConfigurable.setupChangeLists(project, myChangeLists);
    setupField(BY_SOURCE_CHANGES, myChangeLists, null, null, null);
    setupField(BY_SOURCE_POSITION, new JLabel(), null, null, null); // empty stub
    setValidation((configuration) -> {
      ArrayList<ValidationInfo> infos = new ArrayList<>();
      TestObject testObject = configuration.getTestObject();
      JComponent field = myFields[getTestKind()];
      JComponent component = field instanceof ComponentWithBrowseButton ? ((ComponentWithBrowseButton<?>)field).getChildComponent() : field;
      if (testObject instanceof TestMethod) {
        ValidationInfo info = RuntimeConfigurationException.validate(myFields[CLASS], () -> ((TestMethod)testObject).checkClass());
        infos.add(info);
        if (!info.message.isEmpty()) {
          infos.add(new ValidationInfo("", component));
          return infos;
        }
      }
      infos.add(RuntimeConfigurationException.validate(component, () -> testObject.checkConfiguration()));
      return infos;
    });
  }

  @Override
  public JComponent[] getAllComponents() {
    return ArrayUtil.append(myFields, myTypeChooser);
  }

  public int getTestKind() {
    Integer item = myTypeChooser.getItem();
    return item == null ? 0 : item;
  }

  public boolean disableModuleClasspath(boolean wholeProjectSelected) {
    return myModel.disableModuleClasspath(wholeProjectSelected);
  }

  private <T extends JComponent> void setupField(int kind,
                                                 JComponent field,
                                                 @Nullable Object document,
                                                 @Nullable BrowseModuleValueActionListener<T> browser,
                                                 @Nls String hint) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1;
    component().add(field, constraints);
    myFields[kind] = field;
    myHints.put(field, hint);
    if (browser != null) {
      //noinspection unchecked
      browser.setField((ComponentWithBrowseButton<T>)field);
    }
    if (document != null) {
      myModel.setJUnitDocument(kind, document);
    }
  }

  @Override
  public @Nullable String getHint(@Nullable JComponent component) {
    return component == null ? null : myHints.get(component);
  }

  private void kindChanged(int oldKind, int kind) {
    myTypeChooser.setItem(kind);
    Arrays.stream(myFields).forEach(field -> field.setVisible(false));
    myFields[kind].setVisible(true);
    if (METHOD == kind) {
      myFields[CLASS].setVisible(true);
    }
    if (PATTERN == kind  && (oldKind == CLASS || oldKind == METHOD) && StringUtil.isEmpty(((TextAccessor)myFields[PATTERN]).getText())) {
      ((TextAccessor)myFields[PATTERN]).setText(getClassName());
    }
    fireEditorStateChanged();
  }

  private String getClassName() {
    return ((TextAccessor)myFields[CLASS]).getText();
  }

  @Override
  protected void resetEditorFrom(@NotNull JUnitConfiguration s) {
    String[] ids = s.getPersistentData().getUniqueIds();
    myUniqueIdField.setText(ids != null ? StringUtil.join(ids, " ") : null);
    myTagsField.setText(s.getPersistentData().getTags());
    myChangeLists.setSelectedItem(s.getPersistentData().getChangeList());
    myModel.reset(s);
  }

  @Override
  protected void applyEditorTo(@NotNull JUnitConfiguration s) {
    s.getPersistentData().setUniqueIds(JUnitConfigurable.setArrayFromText(myUniqueIdField.getText()));
    s.getPersistentData().setTags(myTagsField.getText());
    s.getPersistentData().setChangeList(myChangeLists.getItem());
    myModel.apply(myModuleSelector.getModule(), s);
    validate(s);
  }
}
