package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

/**
 * @author peter
 */
@State(
    name = "GantSettings",
    storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/gant_config.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GantSettings implements PersistentStateComponent<SdkHomeConfigurable.SdkHomeSettings> {
  private SdkHomeConfigurable.SdkHomeSettings mySdkPath;

  public SdkHomeConfigurable.SdkHomeSettings getState() {
    return mySdkPath;
  }

  public void loadState(SdkHomeConfigurable.SdkHomeSettings state) {
    mySdkPath = state;
  }

  public static GantSettings getInstance(Project project) {
    return ServiceManager.getService(project, GantSettings.class);
  }
}
