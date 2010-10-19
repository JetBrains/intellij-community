package org.jetbrains.android.newProject;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestPropertiesEditor {
  private JPanel myContentPanel;
  private AndroidModulesComboBox myModulesCombo;

  public AndroidTestPropertiesEditor(@NotNull Project project) {
    myModulesCombo.init(project);
  }

  public Module getModule() {
    return myModulesCombo.getModule();
  }

  public void validate() throws ConfigurationException {
    Module module = myModulesCombo.getModule();
    if (module == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.specify.tested.module.error"));
    }
    else if (AndroidFacet.getInstance(module) == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.tested.module.without.facet.error"));
    }
    String moduleDirPath = new File(module.getModuleFilePath()).getParent();
    if (moduleDirPath == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.cannot.find.module.parent.dir.error", moduleDirPath));
    }
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }
}
