package org.jetbrains.android.uipreview;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidLayoutPreviewToolWindow",
  storages = {
    @Storage(id = "AndroidLayoutPreviewToolWindow", file = "$WORKSPACE_FILE$")
  }
)
public class AndroidLayoutPreviewToolWindowSettings implements PersistentStateComponent<AndroidLayoutPreviewToolWindowSettings.MyState> {
  private final Map<VirtualFile, String> myFile2DeviceConfig = new HashMap<VirtualFile, String>();
  private GlobalState myGlobalState = new GlobalState();

  public static AndroidLayoutPreviewToolWindowSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidLayoutPreviewToolWindowSettings.class);
  }
  
  @NotNull
  public GlobalState getGlobalState() {
    return myGlobalState;
  }
  
  public String getDeviceConfiguration(@NotNull VirtualFile file) {
    synchronized (myFile2DeviceConfig) {
      return myFile2DeviceConfig.get(file);
    }
  }
  
  public void setDeviceConfiguration(@NotNull VirtualFile file, @NotNull String deviceConfiguration) {
    synchronized (myFile2DeviceConfig) {
      myFile2DeviceConfig.put(file, deviceConfiguration);
    }
  }

  public void removeDeviceConfiguration(@NotNull VirtualFile file) {
    synchronized (myFile2DeviceConfig) {
      myFile2DeviceConfig.remove(file);
    }
  }

  @Override
  public MyState getState() {
    final Map<String, String> url2DeviceConfig = new HashMap<String, String>();

    synchronized (myFile2DeviceConfig) {
      for (Map.Entry<VirtualFile, String> entry : myFile2DeviceConfig.entrySet()) {
        url2DeviceConfig.put(entry.getKey().getUrl(), entry.getValue());
      }
    }
    final MyState state = new MyState();
    state.setUrl2DeviceConfig(url2DeviceConfig);
    state.setState(myGlobalState);
    return state;
  }

  @Override
  public void loadState(MyState state) {
    myGlobalState = state.getState();
    synchronized (myFile2DeviceConfig) {
      myFile2DeviceConfig.clear();

      for (Map.Entry<String, String> entry : state.getUrl2DeviceConfig().entrySet()) {
        final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(entry.getKey());
        
        if (file != null) {
          myFile2DeviceConfig.put(file, entry.getValue());
        }
      }
    }
  }

  public static class MyState {
    private GlobalState myGlobalState = new GlobalState();
    private Map<String, String> myUrl2DeviceConfig = new HashMap<String, String>();

    public GlobalState getState() {
      return myGlobalState;
    }

    @Tag("device-configurations")
    @MapAnnotation(surroundWithTag = false)
    public Map<String, String> getUrl2DeviceConfig() {
      return myUrl2DeviceConfig;
    }

    public void setState(GlobalState state) {
      myGlobalState = state;
    }

    public void setUrl2DeviceConfig(Map<String, String> url2DeviceConfig) {
      myUrl2DeviceConfig = url2DeviceConfig;
    }
  }

  public static class GlobalState {
    private String myDevice;
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
