package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.gant.GantSettings;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

import javax.swing.*;

/**
 * @author peter
 */
public class GradleConfigurable extends SdkHomeConfigurable {

  public GradleConfigurable(Project project) {
    super(project, "Gradle");
  }

  public Icon getIcon() {
    return GradleLibraryManager.GRADLE_ICON;
  }

  public String getHelpTopic() {
    return "";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    return GradleLibraryManager.isGradleSdkHome(file);
  }

  @Override
  protected GradleSettings getFrameworkSettings() {
    return GradleSettings.getInstance(myProject);
  }

}