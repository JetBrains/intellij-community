package org.jetbrains.android.exportSignedPackage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.ModuleListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class GenerateSignedApkDialog extends DialogWrapper {
  private JCheckBox myProGuardCheckBox;
  private JComboBox myModuleComboBox;
  private JPanel myPanel;
  private JPasswordField myKeyStorePasswordField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JTextField myKeyStorePathField;
  private JPanel myKeyStoreButtonsPanel;
  private JButton myCreateKeyStoreButton;
  private JButton myLoadKeyStoreButton;
  private JBCheckBox myRememberPasswordsCheckBox;
  private TextFieldWithBrowseButton myProGuardConfigFilePathField;
  private JCheckBox myIncludeSystemProGuardFileCheckBox;
  private TextFieldWithBrowseButton.NoPathCompletion myApkPathField;

  protected GenerateSignedApkDialog(@NotNull Project project, @Nullable Module defaultModule) {
    super(project);
    setTitle("Generate Signed APK");

    final List<Module> androidAppModules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        androidAppModules.add(module);
      }
    }
    myModuleComboBox.setModel(
      new CollectionComboBoxModel(androidAppModules, androidAppModules.contains(defaultModule) ? defaultModule : androidAppModules.get(0)));
    myModuleComboBox.setRenderer(new ModuleListCellRendererWrapper(myModuleComboBox.getRenderer()));

    myModuleComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        //To change body of implemented methods use File | Settings | File Templates.
      }
    });

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }


}
