package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFileManager;

import com.intellij.util.ArrayUtil;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ide.util.BrowseFilesListener;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;

import org.jdom.Element;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginModuleBuildConfEditor implements ModuleConfigurationEditor {
  private JPanel myWholePanel = new JPanel(new GridBagLayout());
  private JRadioButton myClasses = new JRadioButton("Classes"); //todo: best Labels
  private JRadioButton myJar = new JRadioButton("Jar");
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
    ButtonGroup deployButtonGroup = new ButtonGroup();
    deployButtonGroup.add(myJar);
    deployButtonGroup.add(myClasses);
    myClasses.setSelected(true);

    myJar.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        deploymentMethodChanged();
      }
    });
    myClasses.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        deploymentMethodChanged();
      }
    });
    myPluginXML.addActionListener(new BrowseFilesListener(myPluginXML.getTextField(), "Select META-INF Directory Location", "The META-INF"+ File.separator + "plugin.xml will be saved in selected directory", BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR));
    myPluginXML.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = !myPluginXML.getText().equals(myBuildProperties.getPluginXMLPath());
      }
    });
    JPanel pluginXmlPanel = new JPanel(new BorderLayout());
    pluginXmlPanel.add(myPluginXMLLabel,  BorderLayout.NORTH);
    pluginXmlPanel.add(myPluginXML, BorderLayout.CENTER);
    myWholePanel.add(pluginXmlPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(10, 5, 15, 5), 0, 0));
    myWholePanel.add(new JLabel("Choose plugin deployment method:"),  new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(15, 5, 0, 5), 0, 0));
    myWholePanel.add(myClasses, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                      GridBagConstraints.NONE, new Insets(0, 5, 0, 5), 0, 0));
    myWholePanel.add(myJar, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.NONE, new Insets(0, 5, 0, 5), 0, 0));
    myWholePanel.add(myDesctination,  new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.SOUTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));

    return myWholePanel;
  }

  private void deploymentMethodChanged() {
    myDesctination.setText(
      myJar.isSelected()
      ? (myBuildProperties.getJarPath() != null ? "Plugin jar will be put to " + myBuildProperties.getJarPath().replace('/', File.separatorChar) : "")
      : (myBuildProperties.getExplodedPath() != null ? "Plugin will be put in " + myBuildProperties.getExplodedPath().replace('/', File.separatorChar) : ""));
    myModified = true;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (!mySetDependencyOnPluginModule.isEmpty()) {
      throw new ConfigurationException("Unable to set dependency on plugin module.");
    }
    final String toDelete = !myJar.isSelected() ? myBuildProperties.getJarPath() != null ? myBuildProperties.getJarPath().replace('/', File.separatorChar) : null :
                                                  myBuildProperties.getExplodedPath() != null ? myBuildProperties.getExplodedPath().replace('/', File.separatorChar) : null;
    if (toDelete != null && new File(toDelete).exists()) {
      if (Messages.showYesNoDialog(myModule.getProject(),
                                                                      (!myJar.isSelected() ? "Delete " : "Clear ") + toDelete + "?",
                                                                      "Clean up plugin directory", null) == DialogWrapper.OK_EXIT_CODE) {

        CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                      new Runnable() {
                                                        public void run() {
                                                          FileUtil.delete(new File(toDelete));
                                                        }
                                                      },
                                                      "Synchronize plugins directory",
                                                      null);
      }
    }
    myBuildProperties.setJarPlugin(myJar.isSelected());
    final File plugin = new File(myBuildProperties.getPluginXMLPath());
    if (plugin.exists() &&
        !plugin.getPath().equals(myPluginXML.getText()) &&
        Messages.showYesNoDialog(myModule.getProject(),
                                 "Delete " + plugin.getPath() + File.separator + "META-INF" + " ?",
                                 "Clean up META-INF directory", null) == DialogWrapper.OK_EXIT_CODE) {

      CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                    new Runnable() {
                                                      public void run() {
                                                        FileUtil.delete(new File(plugin, "META-INF"));
                                                      }
                                                    },
                                                    "Remove old plugin.xml directory",
                                                    null);
    }
    myBuildProperties.setPluginXMLPath(myPluginXML.getText());
    myModified = false;
  }

  public void reset() {
    myJar.setSelected(myBuildProperties.isJarPlugin());
    myClasses.setSelected(!myBuildProperties.isJarPlugin());
    myPluginXML.setText(myBuildProperties.getPluginXMLPath());
    deploymentMethodChanged();
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
