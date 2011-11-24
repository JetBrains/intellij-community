package org.jetbrains.android.uipreview;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidLayoutPreviewToolWindow",
  storages = {
    @Storage(id = "AndroidLayoutPreviewToolWindow", file = "$WORKSPACE_FILE$")
  }
)
public class AndroidLayoutPreviewToolWindowSettings implements PersistentStateComponent<AndroidLayoutPreviewToolWindowSettings.State> {
  private State myState = new State();

  public static AndroidLayoutPreviewToolWindowSettings getInstance(@NotNull Project project) {
      return ServiceManager.getService(project, AndroidLayoutPreviewToolWindowSettings.class);
    }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  @Override
  public State getState() {
    return myState;
  }

  public static class State {
    private String myDevice;
    private String myDeviceConfiguration;
    private String myDockMode;
    private String myNightMode;
    private String myTargetHashString;
    private String myTheme;
    private String myLocaleLanguage;
    private String myLocaleRegion;
    private boolean myVisible = true;
    private boolean myHideForNonLayoutFiles = true;

    public String getDevice() {
      return myDevice;
    }

    public void setDevice(String device) {
      myDevice = device;
    }

    public String getDeviceConfiguration() {
      return myDeviceConfiguration;
    }

    public void setDeviceConfiguration(String deviceConfiguration) {
      myDeviceConfiguration = deviceConfiguration;
    }

    public String getDockMode() {
      return myDockMode;
    }

    public void setDockMode(String dockMode) {
      myDockMode = dockMode;
    }

    public String getNightMode() {
      return myNightMode;
    }

    public void setNightMode(String nightMode) {
      myNightMode = nightMode;
    }

    public String getTargetHashString() {
      return myTargetHashString;
    }

    public void setTargetHashString(String targetHashString) {
      myTargetHashString = targetHashString;
    }

    public String getTheme() {
      return myTheme;
    }

    public void setTheme(String theme) {
      myTheme = theme;
    }

    public String getLocaleLanguage() {
      return myLocaleLanguage;
    }

    public void setLocaleLanguage(String localeLanguage) {
      myLocaleLanguage = localeLanguage;
    }

    public String getLocaleRegion() {
      return myLocaleRegion;
    }

    public void setLocaleRegion(String localeRegion) {
      myLocaleRegion = localeRegion;
    }

    public boolean isVisible() {
      return myVisible;
    }

    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public boolean isHideForNonLayoutFiles() {
      return myHideForNonLayoutFiles;
    }

    public void setHideForNonLayoutFiles(boolean hideForNonLayoutFiles) {
      myHideForNonLayoutFiles = hideForNonLayoutFiles;
    }
  }
}
