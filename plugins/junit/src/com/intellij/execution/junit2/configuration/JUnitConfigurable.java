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
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.ShortenCommandLineModeCombo;
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
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
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
  private final JComponent myPackagePanel;
  private final LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
  private final LabeledComponentNoThrow<TextFieldWithBrowseButton> myDir;
  private final LabeledComponentNoThrow<JPanel> myPattern;
  private final LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
  private final LabeledComponent<EditorTextFieldWithBrowseButton> myMethod;
  private final LabeledComponent<EditorTextFieldWithBrowseButton> myCategory;
  // Fields
  private final JPanel myWholePanel;
  private final LabeledComponentNoThrow<ModuleDescriptionsComboBox> myModule;
  private final LabeledComponentNoThrow<JCheckBox> myUseModulePath;
  private final CommonJavaParametersPanel myCommonJavaParameters;
  private final JRadioButton myWholeProjectScope;
  private final JRadioButton mySingleModuleScope;
  private final JRadioButton myModuleWDScope;
  private final TextFieldWithBrowseButton myPatternTextField;
  private final JrePathEditor myJrePathEditor;
  private final LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private final JComboBox<String> myForkCb;
  private final JBLabel myTestLabel;
  private final JComboBox<Integer> myTypeChooser;
  private final JBLabel mySearchForTestsLabel;
  private final JPanel myScopesPanel;
  private final JComboBox<String> myRepeatCb;
  private final JTextField myRepeatCountField;
  private final LabeledComponentNoThrow<JComboBox<String>> myChangeListLabeledComponent;
  private final LabeledComponentNoThrow<RawCommandLineEditor> myUniqueIdField;
  private final LabeledComponentNoThrow<RawCommandLineEditor> myTagsField;
  private final Project myProject;
  private JComponent anchor;

  public JUnitConfigurable(final Project project) {
    myProject = project;
    {
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
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myWholePanel = new JPanel();
      myWholePanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridBagLayout());
      myWholePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTestLabel = new JBLabel();
      myTestLabel.setHorizontalAlignment(2);
      myTestLabel.setHorizontalTextPosition(2);
      myTestLabel.setIconTextGap(4);
      this.$$$loadLabelText$$$(myTestLabel, this.$$$getMessageFromBundle$$$("messages/ExecutionBundle",
                                                                            "junit.configuration.configure.junit.test.kind.label"));
      GridBagConstraints gbc;
      gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weighty = 1.0;
      gbc.anchor = GridBagConstraints.WEST;
      panel1.add(myTestLabel, gbc);
      myTypeChooser = new JComboBox();
      gbc = new GridBagConstraints();
      gbc.gridx = 1;
      gbc.gridy = 0;
      gbc.weighty = 1.0;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = new Insets(0, 10, 0, 0);
      panel1.add(myTypeChooser, gbc);
      final JPanel spacer1 = new JPanel();
      gbc = new GridBagConstraints();
      gbc.gridx = 2;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      panel1.add(spacer1, gbc);
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
      myWholePanel.add(panel2, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
      myCommonJavaParameters = new CommonJavaParametersPanel();
      panel2.add(myCommonJavaParameters, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myModule = new LabeledComponentNoThrow();
      myModule.setComponentClass("com.intellij.application.options.ModuleDescriptionsComboBox");
      myModule.setEnabled(true);
      myModule.setLabelLocation("West");
      myModule.setText(
        this.$$$getMessageFromBundle$$$("messages/ExecutionBundle", "application.configuration.use.classpath.and.jdk.of.module.label"));
      panel2.add(myModule, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUseModulePath = new LabeledComponentNoThrow();
      myUseModulePath.setComponentClass("javax.swing.JCheckBox");
      myUseModulePath.setEnabled(true);
      myUseModulePath.setLabelLocation("West");
      myUseModulePath.setText("");
      panel2.add(myUseModulePath, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myShortenClasspathModeCombo.setEnabled(true);
      myShortenClasspathModeCombo.setLabelLocation("West");
      myShortenClasspathModeCombo.setText(
        this.$$$getMessageFromBundle$$$("messages/ExecutionBundle", "application.configuration.shorten.command.line.label"));
      panel2.add(myShortenClasspathModeCombo,
                 new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer2 = new Spacer();
      panel2.add(spacer2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 10), null, 0, false));
      final Spacer spacer3 = new Spacer();
      panel2.add(spacer3, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                              null, 0, false));
      myJrePathEditor = new JrePathEditor();
      panel2.add(myJrePathEditor, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new GridLayoutManager(1, 6, new Insets(0, 0, 0, 0), -1, -1));
      myWholePanel.add(panel3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer4 = new Spacer();
      panel3.add(spacer4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "repeat.label"));
      panel3.add(label1,
                 new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JBLabel jBLabel1 = new JBLabel();
      this.$$$loadLabelText$$$(jBLabel1, this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "fork.mode.label"));
      panel3.add(jBLabel1,
                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myForkCb = new JComboBox();
      panel3.add(myForkCb, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
      myRepeatCountField = new JTextField();
      myRepeatCountField.setColumns(3);
      panel3.add(myRepeatCountField, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
      myRepeatCb = new JComboBox();
      panel3.add(myRepeatCb, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      final JSeparator separator1 = new JSeparator();
      myWholePanel.add(separator1, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new GridLayoutManager(10, 1, new Insets(0, 0, 0, 0), -1, -1));
      myWholePanel.add(panel4, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
      myMethod.setEnabled(true);
      myMethod.setLabelLocation("West");
      myMethod.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.method.label"));
      panel4.add(myMethod, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPackagePanel = new JPanel();
      myPackagePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel4.add(myPackagePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
      myPackage.setEnabled(true);
      myPackage.setLabelLocation("West");
      myPackage.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.package.label"));
      myPackage.setVisible(true);
      myPackagePanel.add(myPackage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPattern = new LabeledComponentNoThrow();
      myPattern.setComponentClass("javax.swing.JPanel");
      myPattern.setLabelLocation("West");
      myPattern.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "pattern.label"));
      myPattern.setVisible(true);
      panel4.add(myPattern, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myClass = new LabeledComponentNoThrow();
      myClass.setLabelLocation("West");
      myClass.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.class.label"));
      panel4.add(myClass, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myCategory.setLabelLocation("West");
      myCategory.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "category.label"));
      panel4.add(myCategory, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUniqueIdField = new LabeledComponentNoThrow();
      myUniqueIdField.setComponentClass("com.intellij.ui.RawCommandLineEditor");
      myUniqueIdField.setLabelLocation("West");
      myUniqueIdField.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "uniqueid.label"));
      panel4.add(myUniqueIdField, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myDir = new LabeledComponentNoThrow();
      myDir.setComponentClass("com.intellij.openapi.ui.TextFieldWithBrowseButton");
      myDir.setLabelLocation("West");
      myDir.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "directory.label"));
      panel4.add(myDir, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myChangeListLabeledComponent = new LabeledComponentNoThrow();
      myChangeListLabeledComponent.setComponentClass("javax.swing.JComboBox");
      myChangeListLabeledComponent.setLabelLocation("West");
      myChangeListLabeledComponent.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "change.list.label"));
      panel4.add(myChangeListLabeledComponent,
                 new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myScopesPanel = new JPanel();
      myScopesPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
      panel4.add(myScopesPanel, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      mySearchForTestsLabel = new JBLabel();
      this.$$$loadLabelText$$$(mySearchForTestsLabel,
                               this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.search.for.tests.label"));
      myScopesPanel.add(mySearchForTestsLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                   null, null, 0, false));
      myWholeProjectScope = new JRadioButton();
      this.$$$loadButtonText$$$(myWholeProjectScope,
                                this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.in.whole.project.radio"));
      myScopesPanel.add(myWholeProjectScope, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      final Spacer spacer5 = new Spacer();
      myScopesPanel.add(spacer5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      mySingleModuleScope = new JRadioButton();
      this.$$$loadButtonText$$$(mySingleModuleScope,
                                this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "junit.configuration.in.single.module.radio"));
      myScopesPanel.add(mySingleModuleScope, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      myModuleWDScope = new JRadioButton();
      this.$$$loadButtonText$$$(myModuleWDScope, this.$$$getMessageFromBundle$$$("messages/JUnitBundle",
                                                                                 "junit.configuration.across.module.dependencies.radio"));
      myScopesPanel.add(myModuleWDScope, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTagsField = new LabeledComponentNoThrow();
      myTagsField.setComponentClass("com.intellij.ui.RawCommandLineEditor");
      myTagsField.setLabelLocation("West");
      myTagsField.setText(this.$$$getMessageFromBundle$$$("messages/JUnitBundle", "tag.expression.label"));
      panel4.add(myTagsField, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myTestLabel.setLabelFor(myTypeChooser);
      label1.setLabelFor(myRepeatCb);
    }
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

    myBrowsers = createBrowsers(project, myModuleSelector, myPackage.getComponent(), myPatternTextField, myCategory.getComponent(),
                                () -> getClassName());
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
  public JComponent $$$getRootComponent$$$() { return myWholePanel; }

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
                                   ReadAction.nonBlocking(() -> FilenameIndex.getFilesByName(myProject, PsiJavaModule.MODULE_INFO_FILE,
                                                                                             GlobalSearchScope.projectScope(
                                                                                               myProject)).length > 0)
                                     .expireWith(this)
                                     .finishOnUiThread(ModalityState.stateForComponent(myUseModulePath),
                                                       visible -> myUseModulePath.setVisible(visible))
                                     .submit(NonUrgentExecutor.getInstance()));
    }
  }

  private void changePanel() {
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
    for (int i = 0; i < myTestLocations.length; i++) {
      getTestLocation(i).setEnabled(enabledFields != null && enabledFields.contains(i));
    }
    /*if (newType == JUnitConfigurationModel.PATTERN) {
      myModule.setEnabled(false);
    } else */
    if (newType != JUnitConfigurationModel.ALL_IN_PACKAGE &&
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
        final JUnitConfiguration configurationCopy =
          new JUnitConfiguration(JUnitBundle.message("default.junit.configuration.name"), getProject());
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
              return JUnitUtil.isTestClass(aClass, true, true);
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