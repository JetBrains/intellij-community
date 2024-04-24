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

import com.intellij.CommonBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LanguageTextField;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;
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

    private final JCheckBox fullyQualifiedName = new JCheckBox(JavaBundle.message("generate.tostring.fully.qualified.class.name"));
    private final JCheckBox enableMethods = new JCheckBox(JavaBundle.message("generate.tostring.getters.in.generated.code"));
    private final JCheckBox moveCaretToMethod = new JCheckBox(JavaBundle.message("generate.tostring.move.to.generated.checkbox"));

    private JRadioButton[] initialValueForReplaceDialog;
    private JRadioButton[] initialValueForNewMethodDialog;

    private final JCheckBox filterConstant = new JCheckBox(JavaBundle.message("generate.tostring.exclude.constant.fields"));
    private final JCheckBox filterEnum = new JCheckBox(JavaBundle.message("generate.tostring.exclude.enum.fields"));
    private final JCheckBox filterStatic = new JCheckBox(JavaBundle.message("generate.tostring.exclude.static.fields"));
    private final JCheckBox filterTransient = new JCheckBox(JavaBundle.message("generate.tostring.exclude..transient"));
    private final JCheckBox filterLoggers = new JCheckBox(JavaBundle.message("generate.tostring.exclude.logger"));
    private final LanguageTextField filterFieldName;
    private final LanguageTextField filterFieldType;
    private final LanguageTextField filterMethodName;
    private final LanguageTextField filterMethodType;
    private final JComboBox<String> sortElementsComboBox = new ComboBox<>();
    private final JCheckBox sortElements = new JCheckBox(JavaBundle.message("generate.tostring.sort.checkbox"));

    /**
     * Constructor.
     *
     * @param config Configuration for this UI to display.
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
        panel.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("generate.tostring.settings")));
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
        sortElementsComboBox.addItem(JavaBundle.message("generate.tostring.sort.ascending"));
        sortElementsComboBox.addItem(JavaBundle.message("generate.tostring.sort.descending"));
        sortElementsComboBox.addItem(JavaBundle.message("generate.tostring.sort.super"));
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
        panel.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("generate.tostring.already.exist.border")));
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
        panel.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("generate.tostring.insert.border")));
        for (JRadioButton anInitialValueForNewMethodDialog : initialValueForNewMethodDialog) {
            panel.add(anInitialValueForNewMethodDialog);
        }
        constraint.gridx = 0;
        constraint.gridy = 2;
        outer.add(panel, constraint);

        // UI Layout - Exclude fields
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder(CommonBundle.message("button.exclude")));
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
        innerPanel.add(new JLabel(JavaBundle.message("generate.tostring.exclude.by.field.name")));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterFieldName);
        filterFieldName.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel(JavaBundle.message("generate.tostring.exclude.by.field.type")));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterFieldType);
        filterFieldType.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel(JavaBundle.message("generate.tostring.exclude.by.name")));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterMethodName);
        filterMethodName.setMinimumSize(JBUI.size(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel(JavaBundle.message("generate.tostring.exclude.by.return.type")));
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
          ConflictResolutionOptionAction action = (ConflictResolutionOptionAction)anInitialValueForReplaceDialog.getAction();
          if (action.option.equals(option)) {
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

        int sort = config.getSortElements();
        sortElements.setSelected(sort != 0);
        sortElementsComboBox.setEnabled(sortElements.isSelected());
        if (sort == 0 || sort == 1) {
            sortElementsComboBox.setSelectedIndex(0);
        } else if (sort == 2) {
            sortElementsComboBox.setSelectedIndex(1);
        } else if (sort == 3) {
            sortElementsComboBox.setSelectedIndex(2);
        }
    }

    private static @Nullable String emptyToNull(final String s) {
        if (s != null && s.isEmpty()) return null;
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
        } else {
            config.setSortElements(sortElementsComboBox.getSelectedIndex() + 1);
        }

        return config;
    }

    /**
     * Action for the options for the conflict resolution policy
     */
    private static class ConflictResolutionOptionAction extends AbstractAction {
        public final @NotNull DuplicationPolicy option;

        ConflictResolutionOptionAction(DuplicationPolicy option) {
            super(option.toString());
            this.option = option;
        }

        @Override
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

        @Override
        public void actionPerformed(ActionEvent e) {
        }
    }


    /**
     * Action listener for user checking sort elements
     */
    private class OnSortElements implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            sortElementsComboBox.setEnabled(sortElements.isSelected());
        }
    }
}
