package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import com.intellij.driver.sdk.ui.remote.Component;

@Remote("com.intellij.openapi.wm.IdeFrame")
public interface IdeFrame {
  StatusBar getStatusBar();

  Component getComponent();
}
