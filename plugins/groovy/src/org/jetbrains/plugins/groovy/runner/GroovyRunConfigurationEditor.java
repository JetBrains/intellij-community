// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.runner;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.DefaultJreSelector.SdkFromModuleDependencies;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.util.ScriptFileUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.LabeledComponentNoThrow;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class GroovyRunConfigurationEditor extends SettingsEditor<GroovyScriptRunConfiguration> implements PanelWithAnchor {
  private JPanel myMainPanel;

  private LabeledComponentNoThrow<TextFieldWithBrowseButton> myScriptPathComponent;
  private CommonJavaParametersPanel myCommonJavaParametersPanel;

  private LabeledComponentNoThrow<ModulesComboBox> myModulesComboBoxComponent;
  private JrePathEditor myJrePathEditor;

  private JCheckBox myDebugCB;
  private JCheckBox myAddClasspathCB;

  private JComponent myAnchor;

  public GroovyRunConfigurationEditor(@NotNull Project project) {
    var scriptPath = myScriptPathComponent.getComponent();
    scriptPath.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor(GroovyFileType.GROOVY_FILE_TYPE)
      .withTitle(GroovyBundle.message("script.runner.chooser.title"))
      .withDescription(GroovyBundle.message("script.runner.chooser.description")));

    final ModulesComboBox modulesComboBox = myModulesComboBoxComponent.getComponent();
    modulesComboBox.addActionListener(e -> myCommonJavaParametersPanel.setModuleContext(modulesComboBox.getSelectedModule()));
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Function0<Boolean> productionOnly = () -> {
      VirtualFile script = ScriptFileUtil.findScriptFileByPath(scriptPath.getText());
      return script != null && !fileIndex.isInTestSourceContent(script);
    };
    myJrePathEditor.setDefaultJreSelector(
      new SdkFromModuleDependencies<>(modulesComboBox, ModulesComboBox::getSelectedModule, productionOnly) {
        @Override
        public void addChangeListener(@NotNull Runnable listener) {
          super.addChangeListener(listener);
          scriptPath.getChildComponent().getDocument().addDocumentListener(
            new DocumentAdapter() {
              @Override
              protected void textChanged(@NotNull DocumentEvent e) {
                listener.run();
              }
            }
          );
        }
      });
    myAnchor = UIUtil.mergeComponentsWithAnchor(
      myScriptPathComponent,
      myCommonJavaParametersPanel,
      myModulesComboBoxComponent,
      myJrePathEditor
    );
  }

  @Override
  public void resetEditorFrom(@NotNull GroovyScriptRunConfiguration configuration) {
    myScriptPathComponent.getComponent().setText(configuration.getScriptPath());
    myCommonJavaParametersPanel.reset(configuration);

    myModulesComboBoxComponent.getComponent().setModules(configuration.getValidModules());
    myModulesComboBoxComponent.getComponent().setSelectedModule(configuration.getConfigurationModule().getModule());
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());

    myDebugCB.setSelected(configuration.isDebugEnabled());
    myAddClasspathCB.setSelected(configuration.isAddClasspathToTheRunner());
  }

  @Override
  public void applyEditorTo(@NotNull GroovyScriptRunConfiguration configuration) throws ConfigurationException {
    configuration.setScriptPath(myScriptPathComponent.getComponent().getText().trim());
    myCommonJavaParametersPanel.applyTo(configuration);

    configuration.setModule(myModulesComboBoxComponent.getComponent().getSelectedModule());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());

    configuration.setDebugEnabled(myDebugCB.isSelected());
    configuration.setAddClasspathToTheRunner(myAddClasspathCB.isSelected());
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    return myMainPanel;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    myAnchor = anchor;
    myScriptPathComponent.setAnchor(anchor);
    myCommonJavaParametersPanel.setAnchor(anchor);
    myModulesComboBoxComponent.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
  }
}
