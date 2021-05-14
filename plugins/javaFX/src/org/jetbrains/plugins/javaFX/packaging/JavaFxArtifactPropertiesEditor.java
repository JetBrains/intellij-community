// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.execution.util.ListTableWithButtons;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class JavaFxArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JavaFxArtifactProperties myProperties;

  private JPanel myWholePanel;
  private JTextField myTitleTF;
  private JTextField myVendorTF;
  private JEditorPane myDescriptionEditorPane;
  private TextFieldWithBrowseButton myAppClass;
  private JTextField myVersionTF;
  private JTextField myWidthTF;
  private JTextField myHeightTF;
  private TextFieldWithBrowseButton myHtmlTemplate;
  private JTextField myHtmlPlaceholderIdTF;
  private TextFieldWithBrowseButton myHtmlParams;
  private TextFieldWithBrowseButton myParams;
  private JCheckBox myUpdateInBackgroundCB;
  private JCheckBox myEnableSigningCB;
  private JButton myEditSignCertificateButton;
  private JCheckBox myConvertCssToBinCheckBox;
  private JComboBox<String> myNativeBundleCB;
  private JButton myEditAttributesButton;
  private JButton myEditIconsButton;
  private JavaFxEditCertificatesDialog myDialog;
  private List<JavaFxManifestAttribute> myCustomManifestAttributes;
  private JavaFxApplicationIcons myIcons;
  private JComboBox<String> myMsgOutputLevel;

  public JavaFxArtifactPropertiesEditor(JavaFxArtifactProperties properties, final Project project, Artifact artifact) {
    super();
    myProperties = properties;
    JavaFxApplicationClassBrowser.appClassBrowser(project, artifact).setField(myAppClass);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(PropertiesFileType.INSTANCE);
    myHtmlParams.addBrowseFolderListener(JavaFXBundle.message("javafx.artifact.properties.editor.choose.file.standalone.title" ), JavaFXBundle.message("javafx.artifact.properties.editor.choose.file.standalone.description"), project, descriptor);
    myParams.addBrowseFolderListener(JavaFXBundle.message("javafx.artifact.properties.editor.choose.file.run.in.browser.title"), JavaFXBundle.message("javafx.artifact.properties.editor.choose.file.run.in.browser.description"), project, descriptor);
    myHtmlTemplate.addBrowseFolderListener(JavaFXBundle.message("javafx.artifact.properties.editor.choose.html.file.title"), JavaFXBundle.message("javafx.artifact.properties.editor.choose.html.file.description"), project,
                                           FileChooserDescriptorFactory.createSingleFileDescriptor(HtmlFileType.INSTANCE));
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

    myEditAttributesButton.addActionListener(e -> {
      final CustomManifestAttributesDialog customManifestAttributesDialog =
        new CustomManifestAttributesDialog(myWholePanel, myCustomManifestAttributes);
      if (customManifestAttributesDialog.showAndGet()) {
        myCustomManifestAttributes = customManifestAttributesDialog.getAttrs();
      }
    });
    myEditIconsButton.addActionListener(e -> {
      final JavaFxApplicationIconsDialog iconsDialog = new JavaFxApplicationIconsDialog(myWholePanel, myIcons, project);
      if (iconsDialog.showAndGet()) {
        myIcons = iconsDialog.getIcons();
      }
    });

    final List<String> bundleNames = new ArrayList<>();
    for (JavaFxPackagerConstants.NativeBundles bundle : JavaFxPackagerConstants.NativeBundles.values()) {
      bundleNames.add(bundle.name());
    }
    myNativeBundleCB.setModel(new DefaultComboBoxModel<>(ArrayUtilRt.toStringArray(bundleNames)));

    final List<String> outputLevels = ContainerUtil.map2List(JavaFxPackagerConstants.MsgOutputLevel.values(), Enum::name);
    myMsgOutputLevel.setModel(new DefaultComboBoxModel<>(ArrayUtilRt.toStringArray(outputLevels)));
  }

  @Override
  public String getTabName() {
    return JavaFXBundle.message("java.fx.artifacts.tab.name");
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
    if (isModified(myProperties.getVersion(), myVersionTF)) return true;
    if (isModified(myProperties.getHtmlTemplateFile(), getSystemIndependentPath(myHtmlTemplate))) return true;
    if (isModified(myProperties.getHtmlPlaceholderId(), myHtmlPlaceholderIdTF)) return true;
    if (isModified(myProperties.getHtmlParamFile(), getSystemIndependentPath(myHtmlParams))) return true;
    if (isModified(myProperties.getParamFile(), getSystemIndependentPath(myParams))) return true;
    if (!Comparing.equal(myNativeBundleCB.getSelectedItem(), myProperties.getNativeBundle())) return true;
    final boolean inBackground = Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND);
    if (inBackground != myUpdateInBackgroundCB.isSelected()) return true;
    if (myProperties.isEnabledSigning() != myEnableSigningCB.isSelected()) return true;
    if (myProperties.isConvertCss2Bin() != myConvertCssToBinCheckBox.isSelected()) return true;
    if (myDialog != null) {
      if (isModified(myProperties.getAlias(), myDialog.myPanel.myAliasTF)) return true;
      if (isModified(myProperties.getKeystore(), myDialog.myPanel.myKeystore)) return true;
      final String keypass = myProperties.getKeypass();
      if (isModified(keypass != null ? new String(Base64.getDecoder().decode(keypass), StandardCharsets.UTF_8) : "", myDialog.myPanel.myKeypassTF)) return true;
      final String storepass = myProperties.getStorepass();
      if (isModified(storepass != null ? new String(Base64.getDecoder().decode(storepass), StandardCharsets.UTF_8) : "", myDialog.myPanel.myStorePassTF)) return true;
      if (myProperties.isSelfSigning() != myDialog.myPanel.mySelfSignedRadioButton.isSelected()) return true;
    }

    if (!Comparing.equal(myCustomManifestAttributes, myProperties.getCustomManifestAttributes())) return true;
    if (!Comparing.equal(myIcons, myProperties.getIcons())) return true;
    if (!Comparing.equal(myMsgOutputLevel.getSelectedItem(), myProperties.getMsgOutputLevel())) return true;
    return false;
  }

  private static boolean isModified(final String title, JTextComponent tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }

  private static boolean isModified(final String title, TextFieldWithBrowseButton tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }

  private static boolean isModified(final String title, String value) {
    return !Comparing.strEqual(title, value);
  }

  @Override
  public void apply() {
    myProperties.setTitle(myTitleTF.getText());
    myProperties.setVendor(myVendorTF.getText());
    myProperties.setDescription(myDescriptionEditorPane.getText());
    myProperties.setAppClass(myAppClass.getText());
    myProperties.setVersion(myVersionTF.getText());
    myProperties.setWidth(myWidthTF.getText());
    myProperties.setHeight(myHeightTF.getText());
    myProperties.setHtmlTemplateFile(getSystemIndependentPath(myHtmlTemplate));
    myProperties.setHtmlPlaceholderId(myHtmlPlaceholderIdTF.getText());
    myProperties.setHtmlParamFile(getSystemIndependentPath(myHtmlParams));
    myProperties.setParamFile(getSystemIndependentPath(myParams));
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
      myProperties.setKeypass(!StringUtil.isEmptyOrSpaces(keyPass) ? Base64.getEncoder().encodeToString(keyPass.getBytes(StandardCharsets.UTF_8)) : null);
      final String storePass = String.valueOf(myDialog.myPanel.myStorePassTF.getPassword());
      myProperties.setStorepass(!StringUtil.isEmptyOrSpaces(storePass) ? Base64.getEncoder().encodeToString(storePass.getBytes(StandardCharsets.UTF_8)) : null);
    }

    myProperties.setCustomManifestAttributes(myCustomManifestAttributes);
    myProperties.setIcons(myIcons);
    myProperties.setMsgOutputLevel((String)myMsgOutputLevel.getSelectedItem());
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
    setText(myVersionTF, myProperties.getVersion());
    setSystemDependentPath(myHtmlTemplate, myProperties.getHtmlTemplateFile());
    setText(myHtmlPlaceholderIdTF, myProperties.getHtmlPlaceholderId());
    setSystemDependentPath(myHtmlParams, myProperties.getHtmlParamFile());
    setSystemDependentPath(myParams, myProperties.getParamFile());
    myNativeBundleCB.setSelectedItem(myProperties.getNativeBundle());
    myUpdateInBackgroundCB.setSelected(Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND));
    myEnableSigningCB.setSelected(myProperties.isEnabledSigning());
    myConvertCssToBinCheckBox.setSelected(myProperties.isConvertCss2Bin());
    myEditSignCertificateButton.setEnabled(myProperties.isEnabledSigning());
    myCustomManifestAttributes = myProperties.getCustomManifestAttributes();
    myIcons = myProperties.getIcons();
    myMsgOutputLevel.setSelectedItem(myProperties.getMsgOutputLevel());
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

  static String getSystemIndependentPath(TextFieldWithBrowseButton withBrowseButton) {
    final String text = withBrowseButton.getText();
    if (StringUtil.isEmptyOrSpaces(text)) return null;
    return FileUtil.toSystemIndependentName(text.trim());
  }

  static void setSystemDependentPath(TextFieldWithBrowseButton withBrowseButton, String path) {
    withBrowseButton.setText(path != null ? FileUtil.toSystemDependentName(path.trim()) : "");
  }

  private static class CustomManifestAttributesDialog extends DialogWrapper {
    private final JPanel myWholePanel = new JPanel(new BorderLayout());
    private final AttributesTable myTable;

    protected CustomManifestAttributesDialog(JPanel panel, List<JavaFxManifestAttribute> attrs) {
      super(panel, true);
      myTable = new AttributesTable();
      myTable.setValues(attrs);
      myWholePanel.add(myTable.getComponent(), BorderLayout.CENTER);
      setTitle(JavaFXBundle.message("javafx.artifact.properties.editor.edit.custom.manifest.attributes"));
      init();
    }

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
      return myWholePanel;
    }

    @Override
    protected void doOKAction() {
      myTable.stopEditing();
      super.doOKAction();
    }

    List<JavaFxManifestAttribute> getAttrs() {
      return myTable.getAttrs();
    }

    private static class AttributesTable extends ListTableWithButtons<JavaFxManifestAttribute> {
      @Override
      protected ListTableModel createListModel() {
        final ColumnInfo name = new ElementsColumnInfoBase<JavaFxManifestAttribute>(JavaFXBundle.message(
          "column.name.artifact.manifest.property.name")) {
          @Nullable
          @Override
          public String valueOf(JavaFxManifestAttribute attribute) {
            return attribute.getName();
          }

          @Override
          public boolean isCellEditable(JavaFxManifestAttribute attr) {
            return true;
          }

          @Override
          public void setValue(JavaFxManifestAttribute attr, String value) {
            attr.setName(value);
          }

          @Nullable
          @Override
          protected String getDescription(JavaFxManifestAttribute element) {
            return element.getName();
          }
        };

        final ColumnInfo value = new ElementsColumnInfoBase<JavaFxManifestAttribute>(JavaFXBundle.message("column.name.artifact.manifest.property.value")) {
          @Override
          public String valueOf(JavaFxManifestAttribute attr) {
            return attr.getValue();
          }

          @Override
          public boolean isCellEditable(JavaFxManifestAttribute attr) {
            return true;
          }

          @Override
          public void setValue(JavaFxManifestAttribute attr, String s) {
            attr.setValue(s);
          }

          @Nullable
          @Override
          protected String getDescription(JavaFxManifestAttribute attr) {
            return attr.getValue();
          }
        };

        return new ListTableModel(name, value);
      }

      @Override
      protected JavaFxManifestAttribute createElement() {
        return new JavaFxManifestAttribute("", "");
      }

      @Override
      protected boolean isEmpty(JavaFxManifestAttribute element) {
        return StringUtil.isEmpty(element.getName()) && StringUtil.isEmpty(element.getValue());
      }

      @Override
      protected JavaFxManifestAttribute cloneElement(JavaFxManifestAttribute attribute) {
        return new JavaFxManifestAttribute(attribute.getName(), attribute.getValue());
      }

      @Override
      protected boolean canDeleteElement(JavaFxManifestAttribute selection) {
        return true;
      }

      public List<JavaFxManifestAttribute> getAttrs() {
        return getElements();
      }
    }
  }
}