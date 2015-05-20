package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.GreclipseSettings;

@State(
  name = GreclipseSettings.COMPONENT_NAME,
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + '/' + GreclipseSettings.COMPONENT_FILE, scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GreclipseIdeaCompilerSettings implements PersistentStateComponent<GreclipseSettings> {
  private final GreclipseSettings mySettings = new GreclipseSettings();

  @Override
  public GreclipseSettings getState() {
    return mySettings;
  }

  @Override
  public void loadState(GreclipseSettings state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  @NotNull
  public static GreclipseSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, GreclipseIdeaCompilerSettings.class).mySettings;
  }
}
