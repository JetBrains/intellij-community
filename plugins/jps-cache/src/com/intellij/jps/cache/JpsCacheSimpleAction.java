package com.intellij.jps.cache;

import com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient;
import com.intellij.jps.cache.client.JpsCacheServerClient;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;

import java.io.IOException;
import java.util.Set;

public class JpsCacheSimpleAction extends AnAction {
  private JpsCacheServerClient myCacheServerClient = new ArtifactoryJpsCacheServerClient();

  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        Set<String> cacheKeys = myCacheServerClient.getAllCacheKeys();
        Set<String> binaryKeys = myCacheServerClient.getAllBinaryKeys();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
  }
}
