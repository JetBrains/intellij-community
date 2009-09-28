package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.util.SdkHomeSettings;

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
public class GradleSettings extends SdkHomeSettings {

  public static GradleSettings getInstance(Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }
}