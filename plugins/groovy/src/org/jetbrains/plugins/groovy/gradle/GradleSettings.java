package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

/**
 * @author peter
 */
@State(
    name = "GradleSettings",
    storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleSettings implements PersistentStateComponent<SdkHomeConfigurable.SdkHomeSettings> {
  private SdkHomeConfigurable.SdkHomeSettings mySdkPath;

  public SdkHomeConfigurable.SdkHomeSettings getState() {
    return mySdkPath;
  }

  public void loadState(SdkHomeConfigurable.SdkHomeSettings state) {
    mySdkPath = state;
  }

  public static GradleSettings getInstance(Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }
}