package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdkConfigurable implements AdditionalDataConfigurable{
  private JLabel mySandboxHomeLabel = new JLabel("Sandbox Home:"); //todo best name
  private TextFieldWithBrowseButton mySandboxHome = new TextFieldWithBrowseButton();

  private Sdk myIdeaJdk;

  private boolean myModified = false;

  public void setSdk(Sdk sdk) {
    myIdeaJdk = sdk;
  }

  public JComponent createComponent() {
    JPanel wholePanel = new JPanel(new BorderLayout());
    wholePanel.add(mySandboxHomeLabel, BorderLayout.WEST);
    wholePanel.add(mySandboxHome, BorderLayout.CENTER);
    mySandboxHome.addBrowseFolderListener("SandBox Home", "Browse folder to put config, system and plugins for target IDEA", null, new FileChooserDescriptor(false, true, false, false, false, false));
    mySandboxHome.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.setText("");
    JPanel doNotExpandPanel = new JPanel(new BorderLayout());
    doNotExpandPanel.add(wholePanel, BorderLayout.NORTH);
    return doNotExpandPanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (mySandboxHome.getText() == null || mySandboxHome.getText().length() == 0){
      throw new ConfigurationException("Please, configure the sandbox.");
    }
    Sandbox sandbox = new Sandbox(mySandboxHome.getText());
    final SdkModificator modificator = myIdeaJdk.getSdkModificator();
    modificator.setSdkAdditionalData(sandbox);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
    myModified = false;
  }

  public void reset() {
    if (myIdeaJdk != null && myIdeaJdk.getSdkAdditionalData() instanceof Sandbox){
      mySandboxHome.setText(((Sandbox)myIdeaJdk.getSdkAdditionalData()).getSandboxHome());
    }
    myModified = false;
  }

  public void disposeUIResources() {
  }
}
