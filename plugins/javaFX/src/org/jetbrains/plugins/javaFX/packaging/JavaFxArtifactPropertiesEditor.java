/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Base64Converter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 3/12/13
 */
public class JavaFxArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JavaFxArtifactProperties myProperties;

  private JPanel myWholePanel;
  private JTextField myTitleTF;
  private JTextField myVendorTF;
  private JEditorPane myDescriptionEditorPane;
  private TextFieldWithBrowseButton myAppClass;
  private JTextField myWidthTF;
  private JTextField myHeightTF;
  private TextFieldWithBrowseButton myHtmlParams;
  private TextFieldWithBrowseButton myParams;
  private JCheckBox myUpdateInBackgroundCB;
  private JCheckBox myEnableSigningCB;
  private JButton myEditSignCertificateButton;
  private JCheckBox myConvertCssToBinCheckBox;
  private JComboBox myNativeBundleCB;
  private JavaFxEditCertificatesDialog myDialog;

  public JavaFxArtifactPropertiesEditor(JavaFxArtifactProperties properties, final Project project, Artifact artifact) {
    super();
    myProperties = properties;
    new JavaFxApplicationClassBrowser(project, artifact).setField(myAppClass);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.PROPERTIES);
    myHtmlParams.addBrowseFolderListener("Choose Properties File", "Parameters for the resulting application to run standalone.", project, descriptor);
    myParams.addBrowseFolderListener("Choose Properties File", "Parameters for the resulting application to run in the browser.", project, descriptor);
    myEditSignCertificateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDialog = new JavaFxEditCertificatesDialog(myWholePanel, myProperties, project);
        myDialog.show();
      }
    });
    myEnableSigningCB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEditSignCertificateButton.setEnabled(myEnableSigningCB.isSelected());
      }
    });

    final List<String> bundleNames = new ArrayList<String>();
    for (JavaFxPackagerConstants.NativeBundles bundle : JavaFxPackagerConstants.NativeBundles.values()) {
      bundleNames.add(bundle.name());
    }
    myNativeBundleCB.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(bundleNames)));
  }

  @Override
  public String getTabName() {
    return "Java FX";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    if (isModified(myProperties.getTitle(), myTitleTF)) return true;
    if (isModified(myProperties.getVendor(), myVendorTF)) return true;
    if (isModified(myProperties.getDescription(), myDescriptionEditorPane)) return true;
    if (isModified(myProperties.getWidth(), myWidthTF)) return true;
    if (isModified(myProperties.getHeight(), myHeightTF)) return true;
    if (isModified(myProperties.getAppClass(), myAppClass)) return true;
    if (isModified(myProperties.getHtmlParamFile(), myHtmlParams)) return true;
    if (isModified(myProperties.getParamFile(), myParams)) return true;
    if (!Comparing.equal(myNativeBundleCB.getSelectedItem(), myProperties.getNativeBundle())) return true;
    final boolean inBackground = Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND);
    if (inBackground != myUpdateInBackgroundCB.isSelected()) return true;
    if (myProperties.isEnabledSigning() != myEnableSigningCB.isSelected()) return true;
    if (myProperties.isConvertCss2Bin() != myConvertCssToBinCheckBox.isSelected()) return true;
    if (myDialog != null) {
      if (isModified(myProperties.getAlias(), myDialog.myPanel.myAliasTF)) return true;
      if (isModified(myProperties.getKeystore(), myDialog.myPanel.myKeystore)) return true;
      final String keypass = myProperties.getKeypass();
      if (isModified(keypass != null ? Base64Converter.decode(keypass) : "", myDialog.myPanel.myKeypassTF)) return true;
      final String storepass = myProperties.getStorepass();
      if (isModified(storepass != null ? Base64Converter.decode(storepass) : "", myDialog.myPanel.myStorePassTF)) return true;
      if (myProperties.isSelfSigning() != myDialog.myPanel.mySelfSignedRadioButton.isSelected()) return true;
    }
    return false;
  }

  private static boolean isModified(final String title, JTextComponent tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }
  
  private static boolean isModified(final String title, TextFieldWithBrowseButton tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }

  @Override
  public void apply() {
    myProperties.setTitle(myTitleTF.getText());
    myProperties.setVendor(myVendorTF.getText());
    myProperties.setDescription(myDescriptionEditorPane.getText());
    myProperties.setAppClass(myAppClass.getText());
    myProperties.setWidth(myWidthTF.getText());
    myProperties.setHeight(myHeightTF.getText());
    myProperties.setHtmlParamFile(myHtmlParams.getText());
    myProperties.setParamFile(myParams.getText());
    myProperties.setUpdateMode(myUpdateInBackgroundCB.isSelected() ? JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND 
                                                                   : JavaFxPackagerConstants.UPDATE_MODE_ALWAYS);
    myProperties.setEnabledSigning(myEnableSigningCB.isSelected());
    myProperties.setConvertCss2Bin(myConvertCssToBinCheckBox.isSelected());
    myProperties.setNativeBundle((String)myNativeBundleCB.getSelectedItem());
    if (myDialog != null) {
      myProperties.setSelfSigning(myDialog.myPanel.mySelfSignedRadioButton.isSelected());
      myProperties.setAlias(myDialog.myPanel.myAliasTF.getText());
      myProperties.setKeystore(myDialog.myPanel.myKeystore.getText());
      final String keyPass = String.valueOf((myDialog.myPanel.myKeypassTF.getPassword()));
      myProperties.setKeypass(!StringUtil.isEmptyOrSpaces(keyPass) ? Base64Converter.encode(keyPass) : null);
      final String storePass = String.valueOf(myDialog.myPanel.myStorePassTF.getPassword());
      myProperties.setStorepass(!StringUtil.isEmptyOrSpaces(storePass) ? Base64Converter.encode(storePass) : null);
    }
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "Project_Structure_Artifacts_Java_FX_tab";
  }

  @Override
  public void reset() {
    setText(myTitleTF, myProperties.getTitle());
    setText(myVendorTF, myProperties.getVendor());
    setText(myDescriptionEditorPane, myProperties.getDescription());
    setText(myWidthTF, myProperties.getWidth());
    setText(myHeightTF, myProperties.getHeight());
    setText(myAppClass, myProperties.getAppClass());
    setText(myHtmlParams, myProperties.getHtmlParamFile());
    setText(myParams, myProperties.getParamFile());
    myNativeBundleCB.setSelectedItem(myProperties.getNativeBundle());
    myUpdateInBackgroundCB.setSelected(Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND));
    myEnableSigningCB.setSelected(myProperties.isEnabledSigning());
    myConvertCssToBinCheckBox.setSelected(myProperties.isConvertCss2Bin());
    myEditSignCertificateButton.setEnabled(myProperties.isEnabledSigning());
  }

  private static void setText(TextFieldWithBrowseButton tf, final String title) {
    if (title != null) {
      tf.setText(title.trim());
    }
  }

  private static void setText(JTextComponent tf, final String title) {
    if (title != null) {
      tf.setText(title.trim());
    }
  }

  @Override
  public void disposeUIResources() {
    if (myDialog != null) {
      myDialog.myPanel = null;
    }
  }
}
