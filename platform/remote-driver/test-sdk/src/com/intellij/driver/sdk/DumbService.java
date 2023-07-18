package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;

@Remote("com.intellij.openapi.project.DumbService")
public interface DumbService {
  boolean isDumb();
}
