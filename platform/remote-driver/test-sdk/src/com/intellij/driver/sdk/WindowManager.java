package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.Nullable;

@Remote("com.intellij.openapi.wm.WindowManager")
public interface WindowManager {
  IdeFrame getIdeFrame(@Nullable Project project);
}
