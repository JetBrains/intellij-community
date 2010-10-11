package org.jetbrains.javafx.run;

import com.intellij.execution.ui.CommonProgramParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxRunConfigurationEditor extends SettingsEditor<JavaFxRunConfiguration> {
  private JPanel myMainPanel;
  private LabeledComponent<TextFieldWithBrowseButton> myMainScript;
  private LabeledComponent<RawCommandLineEditor> myJavaFxParameters;
  private LabeledComponent<JComboBox> myModule;
  private CommonProgramParametersPanel myProgramParametersPanel;

  private final ConfigurationModuleSelector myModuleSelector;

  public JavaFxRunConfigurationEditor(final Project project) {
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myProgramParametersPanel.setModuleContext(myModuleSelector.getModule());
      }
    });

    final TextFieldWithBrowseButton mainScriptComponent = myMainScript.getComponent();
    mainScriptComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
        final VirtualFile[] files = fileChooserDialog.choose(null, project);
        if (files.length != 0) {
          mainScriptComponent.setText(files[0].getPath());
        }
      }
    });
  }

  @Override
  protected void resetEditorFrom(final JavaFxRunConfiguration configuration) {
    myProgramParametersPanel.reset(configuration);
    myModuleSelector.reset(configuration);
    myMainScript.getComponent().setText(configuration.getMainScript());
    myJavaFxParameters.getComponent().setText(configuration.getJavaFxParameters());
  }

  @Override
  protected void applyEditorTo(final JavaFxRunConfiguration configuration) throws ConfigurationException {
    myProgramParametersPanel.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    configuration.setMainScript(myMainScript.getComponent().getText());
    configuration.setJavaFxParameters(myJavaFxParameters.getComponent().getText());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }

  @Override
  protected void disposeEditor() {
  }
}
