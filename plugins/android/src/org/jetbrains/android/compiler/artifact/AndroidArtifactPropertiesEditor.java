package org.jetbrains.android.compiler.artifact;

import com.android.annotations.NonNull;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactPropertiesEditor extends ArtifactPropertiesEditor implements ApkSigningSettingsForm {
  private final AndroidApplicationArtifactProperties myProperties;

  private JPanel myPanel;
  private JRadioButton myDebugRadio;
  private JRadioButton myReleaseSignedRadio;
  private JRadioButton myReleaseUnsignedRadio;
  private JPanel myReleaseKeyPanel;
  private JPasswordField myKeyStorePasswordField;
  private JTextField myKeyStorePathField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JButton myLoadKeyStoreButton;
  private JButton myCreateKeyStoreButton;
  private JCheckBox myProGuardCheckBox;
  private TextFieldWithBrowseButton myProGuardConfigFilePathField;
  private JCheckBox myIncludeSystemProGuardFileCheckBox;
  private JPanel myProGuardConfigPanel;
  private JPanel myKeyStoreButtonsPanel;
  private JPanel myProGuardPanel;

  public AndroidArtifactPropertiesEditor(@Nullable final Artifact artifact,
                                         @NonNull AndroidApplicationArtifactProperties properties,
                                         @NotNull final Project project) {
    myProperties = properties;

    myKeyStoreButtonsPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 5, 0));
    myProGuardPanel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myReleaseKeyPanel, myReleaseSignedRadio.isSelected(), true);
      }
    };
    myDebugRadio.addActionListener(listener);
    myReleaseUnsignedRadio.addActionListener(listener);
    myReleaseSignedRadio.addActionListener(listener);

    AndroidUiUtil.initSigningSettingsForm(project, this);

    myProGuardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myProGuardConfigPanel, myProGuardCheckBox.isSelected(), true);
      }
    });

    myProGuardConfigFilePathField.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String path = myProGuardConfigFilePathField.getText().trim();
        VirtualFile defaultFile = path != null && path.length() > 0
                                  ? LocalFileSystem.getInstance().findFileByPath(path)
                                  : null;
        if (defaultFile == null) {
          final AndroidFacet facet = AndroidArtifactUtil.getPackagedFacet(project, artifact);
          if (facet != null) {
            defaultFile = AndroidRootUtil.getMainContentRoot(facet);
          }
        }
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        final VirtualFile file = FileChooser.chooseFile(descriptor, myPanel, project, defaultFile);
        if (file != null) {
          myProGuardConfigFilePathField.setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });
  }

  private String getKeyStorePath() {
    return myKeyStorePathField.getText().trim();
  }

  @Override
  public String getTabName() {
    return "Android";
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return getSigningMode() != myProperties.getSigningMode() ||
           !getKeyStoreFileUrl().equals(myProperties.getKeyStoreUrl()) ||
           !getKeyStorePassword().equals(myProperties.getPlainKeystorePassword()) ||
           !getKeyAlias().equals(myProperties.getKeyAlias()) ||
           !getKeyPassword().equals(myProperties.getPlainKeyPassword()) ||
           myProGuardCheckBox.isSelected() != myProperties.isRunProGuard() ||
           !getProGuardConfigFileUrl().equals(myProperties.getProGuardCfgFileUrl()) ||
           myIncludeSystemProGuardFileCheckBox.isSelected() != myProperties.isIncludeSystemProGuardCfgFile();
  }

  @Override
  public void apply() {
    myProperties.setSigningMode(getSigningMode());
    myProperties.setKeyStoreUrl(getKeyStoreFileUrl());
    myProperties.setPlainKeystorePassword(getKeyStorePassword());
    myProperties.setKeyAlias(getKeyAlias());
    myProperties.setPlainKeyPassword(getKeyPassword());
    myProperties.setRunProGuard(myProGuardCheckBox.isSelected());
    myProperties.setProGuardCfgFileUrl(getProGuardConfigFileUrl());
    myProperties.setIncludeSystemProGuardCfgFile(myIncludeSystemProGuardFileCheckBox.isSelected());
  }

  private String getProGuardConfigFileUrl() {
    return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(myProGuardConfigFilePathField.getText().trim()));
  }

  @Override
  public void reset() {
    switch (myProperties.getSigningMode()) {
      case RELEASE_UNSIGNED:
        myReleaseUnsignedRadio.setSelected(true);
        myDebugRadio.setSelected(false);
        myReleaseSignedRadio.setSelected(false);
        break;
      case DEBUG:
        myReleaseUnsignedRadio.setSelected(false);
        myDebugRadio.setSelected(true);
        myReleaseSignedRadio.setSelected(false);
        break;
      case RELEASE_SIGNED:
        myReleaseUnsignedRadio.setSelected(false);
        myDebugRadio.setSelected(false);
        myReleaseSignedRadio.setSelected(true);
        break;
    }
    final String keyStoreUrl = myProperties.getKeyStoreUrl();
    myKeyStorePathField.setText(keyStoreUrl != null ? FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(keyStoreUrl)) : "");
    myKeyStorePasswordField.setText(myProperties.getPlainKeystorePassword());

    final String keyAlias = myProperties.getKeyAlias();
    myKeyAliasField.setText(keyAlias != null ? keyAlias : "");
    myKeyPasswordField.setText(myProperties.getPlainKeyPassword());

    myProGuardCheckBox.setSelected(myProperties.isRunProGuard());
    myProGuardConfigFilePathField.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(myProperties.getProGuardCfgFileUrl())));
    myIncludeSystemProGuardFileCheckBox.setSelected(myProperties.isIncludeSystemProGuardCfgFile());

    UIUtil.setEnabled(myReleaseKeyPanel, myProperties.getSigningMode() == AndroidArtifactSigningMode.RELEASE_SIGNED, true);
    UIUtil.setEnabled(myProGuardConfigPanel, myProperties.isRunProGuard(), true);
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  private AndroidArtifactSigningMode getSigningMode() {
    if (myDebugRadio.isSelected()) {
      return AndroidArtifactSigningMode.DEBUG;
    }
    else if (myReleaseSignedRadio.isSelected()) {
      return AndroidArtifactSigningMode.RELEASE_SIGNED;
    }
    return AndroidArtifactSigningMode.RELEASE_UNSIGNED;
  }

  @NotNull
  private String getKeyStoreFileUrl() {
    final String path = getKeyStorePath();
    return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path));
  }

  @NotNull
  private String getKeyStorePassword() {
    return String.valueOf(myKeyStorePasswordField.getPassword());
  }

  @NotNull
  private String getKeyAlias() {
    return myKeyAliasField.getText().trim();
  }

  @NotNull
  private String getKeyPassword() {
    return String.valueOf(myKeyPasswordField.getPassword());
  }

  @Override
  public JButton getLoadKeyStoreButton() {
    return myLoadKeyStoreButton;
  }

  @Override
  public JTextField getKeyStorePathField() {
    return myKeyStorePathField;
  }

  @Override
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public JButton getCreateKeyStoreButton() {
    return myCreateKeyStoreButton;
  }

  @Override
  public JPasswordField getKeyStorePasswordField() {
    return myKeyStorePasswordField;
  }

  @Override
  public TextFieldWithBrowseButton getKeyAliasField() {
    return myKeyAliasField;
  }

  @Override
  public JPasswordField getKeyPasswordField() {
    return myKeyPasswordField;
  }
}
