package com.intellij.appengine.sdk;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class AppEngineSdkManager {

  public static AppEngineSdkManager getInstance() {
    return ServiceManager.getService(AppEngineSdkManager.class);
  }

  @NotNull
  public abstract AppEngineSdk findSdk(@NotNull String sdkPath);

  @NotNull
  public abstract List<? extends AppEngineSdk> getValidSdks();

}
