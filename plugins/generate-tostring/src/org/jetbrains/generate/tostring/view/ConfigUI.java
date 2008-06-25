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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.HyperlinkLabel;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.jetbrains.generate.tostring.GenerateToStringUtils;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.config.ConflictResolutionPolicy;
import org.jetbrains.generate.tostring.config.InsertNewMethodPolicy;
import org.jetbrains.generate.tostring.config.PolicyOptions;
import org.jetbrains.generate.tostring.exception.PluginException;
import org.jetbrains.generate.tostring.exception.TemplateResourceException;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.template.TemplateResourceLocator;
import org.jetbrains.generate.tostring.util.FileUtil;
import org.jetbrains.generate.tostring.util.StringUtil;
import org.jetbrains.generate.tostring.velocity.VelocityFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration User Interface.
 * </p>
 * The configuration is in the menu <b>File - Settings - GenerateToString</b>
 */
public class ConfigUI extends JPanel {



    private static final String DEFAULT_TEMPLATE_FILENAME_EXTENSION = ".vm";
    public static final TemplateResource activeTemplate = new TemplateResource("--> Active Template <--", TemplateResourceLocator.getDefaultTemplateBody());

    private Border etched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

    private JCheckBox fieldChooseDialog = new JCheckBox("Use field chooser dialog");
    private JCheckBox useDefaultConflict = new JCheckBox("Always use default conflict resolution policy (Never show field chooser dialog)");
    private JCheckBox enableInspectionOnTheFly = new JCheckBox("Enable on-the-fly code inspection");
    private JCheckBox fullyQualifiedName  = new JCheckBox("Use fully qualified classname in code generation ($classname)");
    private JCheckBox enableMethods = new JCheckBox("Enable getters in code generation ($methods)");
    private JCheckBox moveCaretToMethod = new JCheckBox("Move caret to generated method");
    private HyperlinkLabel hyperlinkDoc = new HyperlinkLabel("Plugin Documentation");

    private JRadioButton[] initialValueForReplaceDialog;
    private JRadioButton[] initialValueForNewMethodDialog;

    private JCheckBox filterConstant = new JCheckBox("Exclude all constant fields");
    private JCheckBox filterEnum = new JCheckBox("Exclude all enum fields");
    private JCheckBox filterStatic = new JCheckBox("Exclude all static fields");
    private JCheckBox filterTransient = new JCheckBox("Exclude all transient fields");
    private JCheckBox filterLoggers = new JCheckBox("Exclude all logger fields (Log4j, JDK Logging, Jakarta Commons Logging)");
    private JTextField filterFieldName = new JTextField();
    private JTextField filterFieldType = new JTextField();
    private JTextField filterMethodName = new JTextField();
    private JTextField filterMethodType = new JTextField();
    private JComboBox sortElementsComboBox = new JComboBox();
    private JCheckBox sortElements = new JCheckBox("Sort elements (when not using field chooser dialog)");

    private JCheckBox autoAddImplementsSerializable = new JCheckBox("Automatic add implements java.io.Serializable");
    private JCheckBox autoImport = new JCheckBox("Automatic import packages");
    private JTextField autoImportPackages = new JTextField();

    private JComboBox templates;
    private JButton activateNewTemplate = new JButton("Use this template");
    private JButton saveTemplate = new JButton("Save template");
    private JButton syntaxCheck = new JButton("Syntax check");

    private JTextArea methodBody = new JTextArea();

    private TitledBorder templateBodyBorder;
    private JScrollPane templateBodyScrollPane;
    private static final String templateBodyBorderTitle = "Method body - Velocity Macro Language - ";

    private JCheckBox enableTemplateQuickList = new JCheckBox("Enable Template Quick Selection List");
    private TemplateQuickSelectionConfigUI templateQuickSelectionConfigUI;

    /**
     * Constructor.
     *
     * @param  config   Configuration for this UI to display.
     */
    public ConfigUI(Config config) {
        init();
        setConfig(config);
    }

    /**
     * Initializes the GUI.
     * <p/>
     * Creating all the swing controls, panels etc.
     */
    private void init() {
        JTabbedPane pane = new JTabbedPane();
        pane.addTab("Settings", initSettingPanel());
        pane.addTab("Edit Template", initEditTemplatePanel());
        pane.addTab("Template Quick Selection List", initTemplateQuickSelectionPanel());
        add(pane);
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
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Settings"));
        Container innerPanel = Box.createHorizontalBox();
        innerPanel.add(fieldChooseDialog);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(useDefaultConflict);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(enableInspectionOnTheFly);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
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
        ConflictResolutionPolicy[] options = PolicyOptions.getConflictOptions();
        initialValueForReplaceDialog = new JRadioButton[options.length];
        ButtonGroup selection = new ButtonGroup();
        for (int i = 0; i < options.length; i++) {
            initialValueForReplaceDialog[i] = new JRadioButton(new ConflictResolutionOptionAction(options[i]));
            selection.add(initialValueForReplaceDialog[i]);
        }
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Default Conflict Resolution Policy"));
        for (int i = 0; i < initialValueForReplaceDialog.length; i++) {
            panel.add(initialValueForReplaceDialog[i]);
        }
        constraint.gridx = 0;
        constraint.gridy = 1;
        outer.add(panel, constraint);

        // UI Layout - Insert Position
        InsertNewMethodPolicy[] options2 = PolicyOptions.getNewMethodOptions();
        initialValueForNewMethodDialog = new JRadioButton[options2.length];
        ButtonGroup selection2 = new ButtonGroup();
        for (int i = 0; i < options2.length; i++) {
            initialValueForNewMethodDialog[i] = new JRadioButton(new InsertNewMethodOptionAction(options2[i]));
            selection2.add(initialValueForNewMethodDialog[i]);
        }
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Insert New Method Policy"));
        for (int i = 0; i < initialValueForNewMethodDialog.length; i++) {
            panel.add(initialValueForNewMethodDialog[i]);
        }
        constraint.gridx = 0;
        constraint.gridy = 2;
        outer.add(panel, constraint);

        // UI Layout - Exclude fields
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Exclude"));
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

        hyperlinkDoc.addHyperlinkListener(new OnClickHyperlink());
        outer.add(hyperlinkDoc);

        return outer;
    }

    /**
     * Initializes the UI for Edit Templates pane
     *
     * @return  the panel
     */
    private JPanel initEditTemplatePanel() {
        GridBagConstraints constraint = new GridBagConstraints();
        JPanel outer = new JPanel();
        outer.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Automatic"));
        Container innerPanel = Box.createHorizontalBox();
        innerPanel.add(autoAddImplementsSerializable);
        innerPanel.add(Box.createHorizontalGlue());
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        innerPanel.add(autoImport);
        autoImport.addActionListener(new OnSelectAutoImport());
        innerPanel.add(Box.createHorizontalStrut(3));
        innerPanel.add(autoImportPackages);
        panel.add(innerPanel);
        innerPanel = Box.createHorizontalBox();
        panel.add(innerPanel);
        constraint.gridx = 0;
        constraint.gridy++;
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        outer.add(panel, constraint);

        // UI Layout - Templates list
        templates = new JComboBox();
        templates.addActionListener(new OnSelectTemplate());
        templates.setMaximumRowCount(20);
        templates.addItem(activeTemplate);
        reloadTemplates();

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Templates"));
        panel.add(templates);
        panel.add(activateNewTemplate);
        activateNewTemplate.setEnabled(false); // should only be enabled if user selects a new template
        activateNewTemplate.addActionListener(new OnActivateNewTemplate());
        panel.add(saveTemplate);
        saveTemplate.addActionListener(new OnSaveTemplate());
        panel.add(syntaxCheck);
        syntaxCheck.addActionListener(new OnSyntaxCheck());
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        constraint.gridx = 0;
        constraint.gridy++;
        outer.add(panel, constraint);


        // UI Layout - Veloctiy Body
        templateBodyScrollPane = new JScrollPane(methodBody);
        methodBody.setRows(28);
        methodBody.setTabSize(1);
        methodBody.addCaretListener(new OnCaretMoved());
        methodBody.setFont(new Font("monospaced", Font.PLAIN, 12));
        templateBodyBorder = BorderFactory.createTitledBorder(etched, templateBodyBorderTitle);
        templateBodyScrollPane.setBorder(templateBodyBorder);
        templateBodyScrollPane.setMinimumSize(new Dimension(400, 300));
        constraint.gridx = 0;
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        constraint.gridy++;
        outer.add(templateBodyScrollPane, constraint);

        //hyperlinkVelocity.addHyperlinkListener(new OnClickHyperlink("velocity"));
        //outer.add(hyperlinkVelocity);
        
        return outer;
    }

    private void reloadTemplates() {
        List<TemplateResource> list = new ArrayList<TemplateResource>();
        list.addAll(Arrays.asList(TemplateResourceLocator.getAllTemplates()));
        while (templates.getItemCount() > 1) {
            templates.removeItemAt(1); // don't remove the active item it should be fixed in the list all the time
        }
        for (TemplateResource res : list) {
            templates.addItem(res); // JComboBox does not have a add collection method so we must add using a loop
        }
    }

    /**
     * Initializes the UI for Template Quick Selection List
     *
     * @return the panel
     */
    private JPanel initTemplateQuickSelectionPanel() {
        GridBagConstraints constraint = new GridBagConstraints();
        JPanel outer = new JPanel();
        outer.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Settings"));
        panel.add(enableTemplateQuickList);
        constraint.gridx = 0;
        constraint.gridy = 0;
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        outer.add(panel, constraint);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(etched, "Template Quick Selection List"));

        templateQuickSelectionConfigUI = new TemplateQuickSelectionConfigUI(TemplateResourceOrderEntry.class);
        templateQuickSelectionConfigUI.setEntriesEditable(false);
        constraint.gridwidth = GridBagConstraints.REMAINDER;
        constraint.fill = GridBagConstraints.BOTH;
        constraint.gridx = 0;
        constraint.gridy = 0;
        panel.add(templateQuickSelectionConfigUI, constraint);

        JButton moveUp = new JButton("Move Up");
        templateQuickSelectionConfigUI.setUpButton(moveUp);
        constraint.gridx = 0;
        constraint.gridy = 1;
        panel.add(moveUp, constraint);

        JButton moveDown = new JButton("Move Down");
        templateQuickSelectionConfigUI.setDownButton(moveDown);
        constraint.gridx = 0;
        constraint.gridy = 2;
        panel.add(moveDown, constraint);

        JButton remove = new JButton("Remove");
        templateQuickSelectionConfigUI.setRemoveButton(remove);
        constraint.gridx = 0;
        constraint.gridy = 3;
        panel.add(remove, constraint);

        JButton add = new JButton("Add");
        templateQuickSelectionConfigUI.setAddButton(add);
        constraint.gridx = 0;
        constraint.gridy = 4;
        panel.add(add, constraint);
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
        fieldChooseDialog.setSelected(config.isUseFieldChooserDialog());
        useDefaultConflict.setSelected(config.isUseDefaultAlways());
        ConflictResolutionPolicy option = config.getReplaceDialogInitialOption();
        for (int i = 0; i < initialValueForReplaceDialog.length; i++) {
            if (initialValueForReplaceDialog[i].getText().equals(option.toString())) {
                initialValueForReplaceDialog[i].setSelected(true);
            }
        }
        InsertNewMethodPolicy option2 = config.getInsertNewMethodInitialOption();
        for (int i = 0; i < initialValueForNewMethodDialog.length; i++) {
            if (initialValueForNewMethodDialog[i].getText().equals(option2.toString())) {
                initialValueForNewMethodDialog[i].setSelected(true);
            }
        }

        // if no method body then use default
        if (StringUtil.isEmpty(config.getMethodBody())) {
            methodBody.setText(TemplateResourceLocator.getDefaultTemplateBody());
            activeTemplate.setTemplate(methodBody.getText());
        } else {
            methodBody.setText(config.getMethodBody());
            activeTemplate.setTemplate(config.getMethodBody());
        }
        methodBody.setCaretPosition(0); // position 0 to keep the first text visible

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
        autoImport.setSelected(config.isAutoImports());
        autoImportPackages.setText(config.getAutoImportsPackages());
        autoImportPackages.setEnabled(autoImport.isSelected());
        enableInspectionOnTheFly.setSelected(config.inspectionOnTheFly);
        enableMethods.setSelected(config.isEnableMethods());
        enableTemplateQuickList.setSelected(config.isEnableTemplateQuickList());
        templateQuickSelectionConfigUI.initTemplateQuickSelectionList(config.getSelectedQuickTemplates());
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
        config.setUseFieldChooserDialog(fieldChooseDialog.isSelected());
        config.setUseDefaultAlways(useDefaultConflict.isSelected());
        for (int i = 0; i < initialValueForReplaceDialog.length; i++) {
            if (initialValueForReplaceDialog[i].isSelected()) {
                config.setReplaceDialogInitialOption(((ConflictResolutionOptionAction) initialValueForReplaceDialog[i].getAction()).option);
            }
        }
        for (int i = 0; i < initialValueForNewMethodDialog.length; i++) {
            if (initialValueForNewMethodDialog[i].isSelected()) {
                config.setInsertNewMethodInitialOption(((InsertNewMethodOptionAction) initialValueForNewMethodDialog[i].getAction()).option);
            }
        }
        // only set text if selected template is on the active template (index 0)
        if (templates.getSelectedIndex() == 0) {
            // don't store default text in config, so that isModified() check works correctly
            if (!methodBody.getText().equals(TemplateResourceLocator.getDefaultTemplateBody())) {
                config.setMethodBody(methodBody.getText());
            }
            activeTemplate.setTemplate(methodBody.getText());
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
        config.setAutoImportsPackages(autoImportPackages.getText());
        config.setAutoImports(autoImport.isSelected());
        config.setInspectionOnTheFly(enableInspectionOnTheFly.isSelected());
        config.setEnableMethods(enableMethods.isSelected());
        config.setEnableTemplateQuickList(enableTemplateQuickList.isSelected());
        config.setSelectedQuickTemplates(templateQuickSelectionConfigUI.getSelectedQuickTemplates());
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
    private class ConflictResolutionOptionAction extends AbstractAction {
        public final ConflictResolutionPolicy option;

        ConflictResolutionOptionAction(ConflictResolutionPolicy option) {
            super(option.toString());
            this.option = option;
        }

        public void actionPerformed(ActionEvent e) {
        }
    }

    /**
     * Action for the options for the inserting new method
     */
    private class InsertNewMethodOptionAction extends AbstractAction {
        public final InsertNewMethodPolicy option;

        InsertNewMethodOptionAction(InsertNewMethodPolicy option) {
            super(option.toString());
            this.option = option;
        }

        public void actionPerformed(ActionEvent e) {
        }
    }

    /**
     * Action listener for user selecting a new template in the combobox
     */
    private class OnSelectTemplate implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JComboBox cb = (JComboBox) e.getSource();
            if (cb.getSelectedIndex() == 0) {
                activateNewTemplate.setEnabled(false);
                saveTemplate.setEnabled(true);
                syntaxCheck.setEnabled(true);
                methodBody.setEditable(true);
                methodBody.setEnabled(true);
            } else {
                activateNewTemplate.setEnabled(true);
                saveTemplate.setEnabled(false);
                syntaxCheck.setEnabled(false);
                methodBody.setEditable(false);
                methodBody.setEnabled(false);
            }

            TemplateResource selected = (TemplateResource) cb.getSelectedItem();
            methodBody.setText(selected.getTemplate());
            methodBody.setCaretPosition(0); // position 0 to keep the first text visible
        }
    }

    /**
     * Action listener for user activating a new template
     */
    private class OnActivateNewTemplate implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            int exit = Messages.showYesNoDialog("Set this template as the active template?", "Activate New Template", Messages.getQuestionIcon());
            if (exit == JOptionPane.YES_OPTION) {
                TemplateResource selected = (TemplateResource) templates.getSelectedItem();
                activeTemplate.setTemplate(selected.getTemplate());
                methodBody.setText(selected.getTemplate()); // update method body with new body
                templates.setSelectedIndex(0); // set index to active template
            }
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
     * Action listener for user saving the template
     */
    private class OnSaveTemplate implements ActionListener {
        public void actionPerformed(ActionEvent event) {

            // create template plugin-folder if missing
            TemplateResourceLocator.createTemplateFolderIfMissing();

            // setup save dialog
            JFileChooser chooser = new JFileChooser(TemplateResourceLocator.getTemplateFolder());
            chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
            chooser.setApproveButtonToolTipText("Save Template");
            chooser.setMultiSelectionEnabled(false);
            chooser.setDialogTitle("Save Template");
            chooser.setDragEnabled(false);

            // show dialog and save file if user click 'approve'
            int choiceSave = chooser.showSaveDialog(ConfigUI.this);
            if (choiceSave == JFileChooser.APPROVE_OPTION) {
                try {
                    // fix extension of file - append .vm if missing
                    String filename = chooser.getSelectedFile().getPath();
                    if (FileUtil.getFileExtension(filename) == null)
                        filename += DEFAULT_TEMPLATE_FILENAME_EXTENSION;

                    // the template resource
                    TemplateResource res = new TemplateResource(FileUtil.stripFilename(filename), methodBody.getText());

                    // confirm overwrite dialog?
                    boolean existsTemplate = false;
                    int choiceOverwrite = JOptionPane.OK_OPTION; // preset choice to OK for saving file if file does not already exists
                    if (FileUtil.existsFile(filename)) {
                        existsTemplate = true;
                        choiceOverwrite = Messages.showYesNoCancelDialog("A file already exists with the filename. Overwrite existing file?", "File Exists", Messages.getWarningIcon());
                    }

                    // save file if user clicked okay, or file does not eixsts
                    if (choiceOverwrite == JOptionPane.OK_OPTION) {

                        // save the file
                        FileUtil.saveFile(filename, res.getTemplate());

                        // if file does not already exists add it to the template combobox so it is updated
                        if (! existsTemplate)
                            templates.addItem(res);
                    }

                } catch (IOException e) {
                    throw new TemplateResourceException("Error saving template", e);
                }
            }

            // reload resources to be updated
            reloadTemplates();
        }

    }

    /**
     * Action listener for user selecting/deselecting auto import
     */
    private class OnSelectAutoImport implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            JCheckBox cb = (JCheckBox) event.getSource();
            if (cb.isSelected())
                autoImportPackages.setEnabled(true);
            else
                autoImportPackages.setEnabled(false);
        }
    }

    /**
     * Action listener for user clicking syntax check
     */
    private class OnSyntaxCheck implements ActionListener {
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

    /**
     * Action listener for user moving caret in method body text area
     */
    private class OnCaretMoved implements CaretListener {
        public void caretUpdate(CaretEvent event) {
            try {
                int dot = event.getDot(); // dot is the index of the text where the caret is positioned

                // calculate current line and column in text area
                int line = methodBody.getLineOfOffset(dot);
                int col = dot - methodBody.getLineStartOffset(line);

                // (++) because line and column should start with from 1 as Velocty Syntax checker expects
                templateBodyBorder.setTitle(templateBodyBorderTitle + "(line " + ++line + ", column " + ++col + ')');
                templateBodyScrollPane.repaint(); // must repaint to update the border title

            } catch (BadLocationException e) {
                e.printStackTrace(); // must print stacktrace to see caused in IDEA log / console
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Action when user clicks homepage hyperlinkVelocity
     */
    private class OnClickHyperlink implements HyperlinkListener {

        public void hyperlinkUpdate(HyperlinkEvent e) {
            String filename = "file://" + GenerateToStringUtils.getDocumentationFilename();
            BrowserUtil.launchBrowser(filename);
        }
    }


}