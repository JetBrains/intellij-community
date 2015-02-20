/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.view;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LanguageTextField;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.DuplicationPolicy;
import org.jetbrains.java.generate.config.InsertWhere;
import org.jetbrains.java.generate.config.PolicyOptions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Configuration User Interface.
 * </p>
 * The configuration is in the menu <b>File - Settings - GenerateToString</b>
 */
public class ConfigUI extends JPanel {

    private final JCheckBox fullyQualifiedName = new JCheckBox("Use fully qualified class name in code generation ($classname)");
    private final JCheckBox enableMethods = new JCheckBox("Enable getters in code generation ($methods)");
    private final JCheckBox moveCaretToMethod = new JCheckBox("Move caret to generated method");

    private JRadioButton[] initialValueForReplaceDialog;
    private JRadioButton[] initialValueForNewMethodDialog;

    private final JCheckBox filterConstant = new JCheckBox("Exclude constant fields");
    private final JCheckBox filterEnum = new JCheckBox("Exclude enum fields");
    private final JCheckBox filterStatic = new JCheckBox("Exclude static fields");
    private final JCheckBox filterTransient = new JCheckBox("Exclude transient fields");
    private final JCheckBox filterLoggers = new JCheckBox("Exclude logger fields (Log4j, JDK Logging, Jakarta Commons Logging)");
    private final LanguageTextField filterFieldName;
    private final LanguageTextField filterFieldType;
    private final LanguageTextField filterMethodName;
    private final LanguageTextField filterMethodType;
    private final JComboBox sortElementsComboBox = new JComboBox();
    private final JCheckBox sortElements = new JCheckBox("Sort elements");

    /**
     * Constructor.
     *
     * @param config Configuration for this UI to display.
     * @param project
     */
    public ConfigUI(Config config, Project project) {
        super(new BorderLayout());
        filterFieldName = new LanguageTextField(RegExpLanguage.INSTANCE, project, config.getFilterFieldName());
        filterFieldType = new LanguageTextField(RegExpLanguage.INSTANCE, project, config.getFilterFieldType());
        filterMethodName = new LanguageTextField(RegExpLanguage.INSTANCE, project, config.getFilterMethodName());
        filterMethodType = new LanguageTextField(RegExpLanguage.INSTANCE, project, config.getFilterMethodType());
        init();
        setConfig(config);
    }

    /**
     * Initializes the GUI.
     * <p/>
     * Creating all the swing controls, panels etc.
     */
    private void init() {
        JPanel header = new JPanel(new BorderLayout());
        header.add(initSettingPanel(), BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
    }

    /**
     * Initializes the UI for Settings pane
     *
     * @return the panel
     */
    private JPanel initSettingPanel() {
        GridBagConstraints constraint = new GridBagConstraints();
        JPanel outer = new JPanel(new GridBagLayout());

        // UI Layout - Settings
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("Settings", true));
        Container innerPanel = Box.createHorizontalBox();
        innerPanel.add(fullyQualifiedName);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(enableMethods);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(moveCaretToMethod);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(sortElements);
        sortElements.addActionListener(new OnSortElements());
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(sortElementsComboBox);
        panel.add(innerPanel);
        sortElementsComboBox.addItem("Ascending");
        sortElementsComboBox.addItem("Descending");
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        constraint.gridx = 0;
        constraint.gridy = 0;
        constraint.insets.left = 5;
        constraint.insets.right = 5;
        outer.add(panel, constraint);

        // UI Layout - Conflict Resolution
        DuplicationPolicy[] options = PolicyOptions.getConflictOptions();
        initialValueForReplaceDialog = new JRadioButton[options.length];
        ButtonGroup selection = new ButtonGroup();
        for (int i = 0; i < options.length; i++) {
            initialValueForReplaceDialog[i] = new JRadioButton(new ConflictResolutionOptionAction(options[i]));
            selection.add(initialValueForReplaceDialog[i]);
        }
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("When method already exists", true));
        for (JRadioButton anInitialValueForReplaceDialog : initialValueForReplaceDialog) {
            panel.add(anInitialValueForReplaceDialog);
        }
        constraint.gridx = 0;
        constraint.gridy = 1;
        outer.add(panel, constraint);

        // UI Layout - Insert Position
        InsertWhere[] options2 = PolicyOptions.getNewMethodOptions();
        initialValueForNewMethodDialog = new JRadioButton[options2.length];
        ButtonGroup selection2 = new ButtonGroup();
        for (int i = 0; i < options2.length; i++) {
            initialValueForNewMethodDialog[i] = new JRadioButton(new InsertNewMethodOptionAction(options2[i]));
            selection2.add(initialValueForNewMethodDialog[i]);
        }
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("Where to insert?", true));
        for (JRadioButton anInitialValueForNewMethodDialog : initialValueForNewMethodDialog) {
            panel.add(anInitialValueForNewMethodDialog);
        }
        constraint.gridx = 0;
        constraint.gridy = 2;
        outer.add(panel, constraint);

        // UI Layout - Exclude fields
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("Exclude", true));
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(filterConstant);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(filterStatic);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(filterTransient);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(filterEnum);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(filterLoggers);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude fields by name (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterFieldName);
        filterFieldName.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude fields by type name (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterFieldType);
        filterFieldType.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude methods by name (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterMethodName);
        filterMethodName.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude methods by return type name (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterMethodType);
        filterMethodType.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        constraint.gridx = 0;
        constraint.gridy = 3;
        outer.add(panel, constraint);

        return outer;
    }

    /**
     * Set's the GUI's controls to represent the given configuration.
     *
     * @param config configuration parameters.
     */
    public final void setConfig(Config config) {
        fullyQualifiedName.setSelected(config.isUseFullyQualifiedName());
        DuplicationPolicy option = config.getReplaceDialogInitialOption();
        for (JRadioButton anInitialValueForReplaceDialog : initialValueForReplaceDialog) {
            if (anInitialValueForReplaceDialog.getText().equals(option.toString())) {
                anInitialValueForReplaceDialog.setSelected(true);
            }
        }
        InsertWhere option2 = config.getInsertNewMethodInitialOption();
        for (JRadioButton anInitialValueForNewMethodDialog : initialValueForNewMethodDialog) {
            if (anInitialValueForNewMethodDialog.getText().equals(option2.toString())) {
                anInitialValueForNewMethodDialog.setSelected(true);
            }
        }


        filterConstant.setSelected(config.isFilterConstantField());
        filterEnum.setSelected(config.isFilterEnumField());
        filterStatic.setSelected(config.isFilterStaticModifier());
        filterTransient.setSelected(config.isFilterTransientModifier());
        filterLoggers.setSelected(config.isFilterLoggers());

        enableMethods.setSelected(config.isEnableMethods());
        moveCaretToMethod.setSelected(config.isJumpToMethod());

        sortElements.setSelected(config.getSortElements() != 0);
        sortElementsComboBox.setEnabled(sortElements.isSelected());
        if (config.getSortElements() == 0 || config.getSortElements() == 1) {
            sortElementsComboBox.setSelectedIndex(0);
        } else if (config.getSortElements() == 2) {
            sortElementsComboBox.setSelectedIndex(1);
        }
    }

    @Nullable
    private static String emptyToNull(final String s) {
        if (s != null && s.length() == 0) return null;
        return s;
    }

    /**
     * Get's the configuration that the GUI controls represent right now.
     *
     * @return the configuration.
     */
    public final Config getConfig() {
        Config config = new Config();

        config.setUseFullyQualifiedName(fullyQualifiedName.isSelected());

        for (JRadioButton anInitialValueForReplaceDialog : initialValueForReplaceDialog) {
            if (anInitialValueForReplaceDialog.isSelected()) {
                config.setReplaceDialogInitialOption(((ConflictResolutionOptionAction) anInitialValueForReplaceDialog.getAction()).option);
            }
        }

        for (JRadioButton anInitialValueForNewMethodDialog : initialValueForNewMethodDialog) {
            if (anInitialValueForNewMethodDialog.isSelected()) {
                config.setInsertNewMethodInitialOption(((InsertNewMethodOptionAction) anInitialValueForNewMethodDialog.getAction()).option);
            }
        }

        config.setFilterConstantField(filterConstant.isSelected());
        config.setFilterEnumField(filterEnum.isSelected());
        config.setFilterTransientModifier(filterTransient.isSelected());
        config.setFilterLoggers(filterLoggers.isSelected());
        config.setFilterStaticModifier(filterStatic.isSelected());
        config.setFilterFieldName(emptyToNull(filterFieldName.getText()));
        config.setFilterFieldType(emptyToNull(filterFieldType.getText()));
        config.setFilterMethodName(emptyToNull(filterMethodName.getText()));
        config.setFilterMethodType(emptyToNull(filterMethodType.getText()));

        config.setEnableMethods(enableMethods.isSelected());
        config.setJumpToMethod(moveCaretToMethod.isSelected());

        if (!sortElements.isSelected()) {
            config.setSortElements(0);
        } else if (sortElementsComboBox.getSelectedIndex() == 0) {
            config.setSortElements(1); // selected index of 0 is ascending
        } else {
            config.setSortElements(2); // selected index of 0 is ascending
        }

        return config;
    }

    /**
     * Action for the options for the conflict resolution policy
     */
    private static class ConflictResolutionOptionAction extends AbstractAction {
        public final DuplicationPolicy option;

        ConflictResolutionOptionAction(DuplicationPolicy option) {
            super(option.toString());
            this.option = option;
        }

        public void actionPerformed(ActionEvent e) {
        }
    }

    /**
     * Action for the options for the inserting new method
     */
    private static class InsertNewMethodOptionAction extends AbstractAction {
        public final InsertWhere option;

        InsertNewMethodOptionAction(InsertWhere option) {
            super(option.toString());
            this.option = option;
        }

        public void actionPerformed(ActionEvent e) {
        }
    }


    /**
     * Action listener for user checking sort elements
     */
    private class OnSortElements implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            sortElementsComboBox.setEnabled(sortElements.isSelected());
        }
    }
}
