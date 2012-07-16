package org.jetbrains.android.exportSignedPackage;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "GenerateSignedApkSettings",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class GenerateSignedApkSettings implements PersistentStateComponent<GenerateSignedApkSettings> {
  public String KEY_STORE_PATH = "";
  public String KEY_ALIAS = "";
  public boolean REMEMBER_PASSWORDS = false;

  @Override
  public GenerateSignedApkSettings getState() {
    return this;
  }

  @Override
  public void loadState(GenerateSignedApkSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static GenerateSignedApkSettings getInstance(final Project project) {
    return ServiceManager.getService(project, GenerateSignedApkSettings.class);
  }
}
