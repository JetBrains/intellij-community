package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
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

  private boolean myModified;

  public void setSdk(Sdk sdk) {
    myIdeaJdk = sdk;
  }

  public JComponent createComponent() {
    JPanel wholePanel = new JPanel(new GridBagLayout());
    wholePanel.add(mySandboxHomeLabel, new GridBagConstraints(0,GridBagConstraints.RELATIVE, 1,1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0));
    wholePanel.add(mySandboxHome, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1,1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,66,0,0),0,0));
    mySandboxHome.addBrowseFolderListener("Sandbox Home", "Browse folder to put config, system and plugins for target IDEA", null, new FileChooserDescriptor(false, true, false, false, false, false));
    mySandboxHome.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.setText("");
    myModified = true;
    return wholePanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (mySandboxHome.getText() == null || mySandboxHome.getText().length() == 0){
      throw new ConfigurationException("Please configure the sandbox");
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
      myModified = false;
    } else {
      mySandboxHome.setText("");
    }
  }

  public void disposeUIResources() {
  }
}
