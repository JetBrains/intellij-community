/*
 * Copyright 2001-2007 the original author or authors.
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
package org.jetbrains.generate.tostring.view;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.config.DuplicatonPolicy;
import org.jetbrains.generate.tostring.config.InsertWhere;
import org.jetbrains.generate.tostring.config.PolicyOptions;
import org.jetbrains.generate.tostring.exception.PluginException;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.velocity.VelocityFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringWriter;

/**
 * Configuration User Interface.
 * </p>
 * The configuration is in the menu <b>File - Settings - GenerateToString</b>
 */
public class ConfigUI extends JPanel {
    private final Border etched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

    private final JCheckBox fullyQualifiedName = new JCheckBox("Use fully qualified classname in code generation ($classname)");
    private final JCheckBox enableMethods = new JCheckBox("Enable getters in code generation ($methods)");
    private final JCheckBox moveCaretToMethod = new JCheckBox("Move caret to generated method");

    private JRadioButton[] initialValueForReplaceDialog;
    private JRadioButton[] initialValueForNewMethodDialog;

    private final JCheckBox filterConstant = new JCheckBox("Exclude all constant fields");
    private final JCheckBox filterEnum = new JCheckBox("Exclude all enum fields");
    private final JCheckBox filterStatic = new JCheckBox("Exclude all static fields");
    private final JCheckBox filterTransient = new JCheckBox("Exclude all transient fields");
    private final JCheckBox filterLoggers = new JCheckBox("Exclude all logger fields (Log4j, JDK Logging, Jakarta Commons Logging)");
    private final JTextField filterFieldName = new JTextField();
    private final JTextField filterFieldType = new JTextField();
    private final JTextField filterMethodName = new JTextField();
    private final JTextField filterMethodType = new JTextField();
    private final JComboBox sortElementsComboBox = new JComboBox();
    private final JCheckBox sortElements = new JCheckBox("Sort elements");

    private final JCheckBox autoAddImplementsSerializable = new JCheckBox("Automatic add implements java.io.Serializable");

    /**
     * Constructor.
     *
     * @param config Configuration for this UI to display.
     */
    public ConfigUI(Config config) {
        super(new BorderLayout());
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
        JPanel outer = new JPanel();
        outer.setLayout(new GridBagLayout());

        // UI Layout - Settings
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("Settings"));
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
        outer.add(panel, constraint);

        // UI Layout - Conflict Resolution
        DuplicatonPolicy[] options = PolicyOptions.getConflictOptions();
        initialValueForReplaceDialog = new JRadioButton[options.length];
        ButtonGroup selection = new ButtonGroup();
        for (int i = 0; i < options.length; i++) {
            initialValueForReplaceDialog[i] = new JRadioButton(new ConflictResolutionOptionAction(options[i]));
            selection.add(initialValueForReplaceDialog[i]);
        }
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("When method already exists"));
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
        panel.setBorder(IdeBorderFactory.createTitledBorder("Where to insert?"));
        for (JRadioButton anInitialValueForNewMethodDialog : initialValueForNewMethodDialog) {
            panel.add(anInitialValueForNewMethodDialog);
        }
        constraint.gridx = 0;
        constraint.gridy = 2;
        outer.add(panel, constraint);

        // UI Layout - Exclude fields
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(IdeBorderFactory.createTitledBorder("Exclude"));
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
        filterFieldName.setMinimumSize(new Dimension(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude fields by typename (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterFieldType);
        filterFieldType.setMinimumSize(new Dimension(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude methods by name (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterMethodName);
        filterMethodName.setMinimumSize(new Dimension(100, 20)); // avoid input field to small
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(new JLabel("Exclude methods by return typename (reg exp)"));
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(filterMethodType);
        filterMethodType.setMinimumSize(new Dimension(100, 20)); // avoid input field to small
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
        DuplicatonPolicy option = config.getReplaceDialogInitialOption();
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
        filterFieldName.setText(config.getFilterFieldName());
        filterFieldType.setText(config.getFilterFieldType());
        filterMethodName.setText(config.getFilterMethodName());
        filterMethodType.setText(config.getFilterMethodType());

        autoAddImplementsSerializable.setSelected(config.isAddImplementSerializable());
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

        config.setAddImplementSerializable(autoAddImplementsSerializable.isSelected());
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
        public final DuplicatonPolicy option;

        ConflictResolutionOptionAction(DuplicatonPolicy option) {
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

    /**
     * Action listener for user clicking syntax check
     */
    private class OnSyntaxCheck implements ActionListener {
        private final JTextArea methodBody = new JTextArea();
        public void actionPerformed(ActionEvent event) {

            // validate template first
            if (!TemplateResource.isValidTemplate(methodBody.getText())) {
                Messages.showWarningDialog("The template is incompatible with this version of the plugin.", "Incompatible Template");
                return;
            }

            // okay let veloicty do it's syntax check
            try {
                StringWriter sw = new StringWriter();
                VelocityContext vc = new VelocityContext();

                // velocity
                VelocityEngine velocity = VelocityFactory.getVelocityEngine();
                velocity.evaluate(vc, sw, "org.intellij.idea.plugin.tostring.view.ConfigUI$OnSyntaxCheck", methodBody.getText());

                // no errors
                Messages.showMessageDialog("Syntax check complete - no errors found", "Syntax Check", Messages.getInformationIcon());

            } catch (ParseErrorException e) {
                // Syntax Error - display to user
                Messages.showMessageDialog("Syntax Error:\n" + e.getMessage(), "Syntax Error", Messages.getErrorIcon());
            } catch (Exception e) {
                throw new PluginException("Error syntax checking template", e);
            }
        }
    }

}
