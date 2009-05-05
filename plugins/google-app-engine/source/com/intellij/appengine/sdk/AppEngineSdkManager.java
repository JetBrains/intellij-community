package com.intellij.appengine.sdk;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class AppEngineSdkManager {

  public static AppEngineSdkManager getInstance() {
    return ServiceManager.getService(AppEngineSdkManager.class);
  }

  public abstract boolean isClassInWhiteList(@NotNull String className);

  public abstract boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName);
}
