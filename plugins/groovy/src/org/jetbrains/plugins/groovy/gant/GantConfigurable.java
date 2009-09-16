package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

import javax.swing.*;

/**
 * @author peter
 */
public class GantConfigurable extends SdkHomeConfigurable {

  public GantConfigurable(Project project) {
    super(project, "Gant");
  }

  public Icon getIcon() {
    return GantIcons.GANT_ICON_16x16;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.project.gant";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    return GantUtils.isGantSdkHome(file);
  }

  @Override
  protected GantSettings getFrameworkSettings() {
    return GantSettings.getInstance(myProject);
  }

}
