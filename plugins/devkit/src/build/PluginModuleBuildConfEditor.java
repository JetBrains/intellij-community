package org.jetbrains.idea.devkit.build;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.HashSet;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginModuleBuildConfEditor implements ModuleConfigurationEditor {
  private JPanel myWholePanel = new JPanel(new GridBagLayout());
  private JLabel myDesctination = new JLabel();
  private JLabel myPluginXMLLabel = new JLabel("Choose path to META-INF" + File.separator + "plugin.xml:");
  private TextFieldWithBrowseButton myPluginXML = new TextFieldWithBrowseButton();
  private boolean myModified = false;

  private PluginModuleBuildProperties myBuildProperties;
  private ModuleConfigurationState myState;

  private HashSet<String> mySetDependencyOnPluginModule = new HashSet<String>();
  private Module myModule;
  public PluginModuleBuildConfEditor(ModuleConfigurationState state) {
    myModule = state.getRootModel().getModule();
    myBuildProperties = (PluginModuleBuildProperties)ModuleBuildProperties.getInstance(myModule);
    myState = state;
  }

  public JComponent createComponent() {
    myPluginXML.addActionListener(new BrowseFilesListener(myPluginXML.getTextField(), "Select META-INF Directory Location", "The META-INF"+ File.separator + "plugin.xml will be saved in selected directory", BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR));
    myPluginXML.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = !myPluginXML.getText().equals(myBuildProperties.getVirtualFilePointer());
      }
    });
    JPanel pluginXmlPanel = new JPanel(new BorderLayout());
    pluginXmlPanel.add(myPluginXMLLabel,  BorderLayout.NORTH);
    pluginXmlPanel.add(myPluginXML, BorderLayout.CENTER);
    myWholePanel.add(pluginXmlPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(10, 5, 15, 5), 0, 0));
    myWholePanel.add(myDesctination,  new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.SOUTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));

    return myWholePanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (!mySetDependencyOnPluginModule.isEmpty()) {
      throw new ConfigurationException("Unable to set dependency on plugin module.");
    }
    final File plugin = myBuildProperties.getPluginXmlPath() != null ? new File(myBuildProperties.getPluginXmlPath()) : null;
    if (plugin != null &&
        plugin.exists() &&
        !plugin.getPath().equals(myPluginXML.getText()) &&
        Messages.showYesNoDialog(myModule.getProject(),
                                 "Delete " + plugin.getPath() + " ?",
                                 "Clean up META-INF directory", null) == DialogWrapper.OK_EXIT_CODE) {

      CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                    new Runnable() {
                                                      public void run() {
                                                        FileUtil.delete(plugin.getParentFile());
                                                      }
                                                    },
                                                    "Remove old plugin.xml directory",
                                                    null);
    }
    myBuildProperties.setPluginXMLUrl(myPluginXML.getText());
    myModified = false;
  }

  public void reset() {
    myPluginXML.setText(myBuildProperties.getPluginXmlPath());
    myModified = false;
  }

  public void disposeUIResources() {}

  public void saveData() {}

  public String getDisplayName() {
    return "Plugin Deployment";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/nodes/plugin.png");
  }

  public String getHelpTopic() {
    return null; //todo
  }

  public void moduleStateChanged() {
  }

}
