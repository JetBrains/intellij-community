package com.intellij.jps.cache;

import com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient;
import com.intellij.jps.cache.client.JpsCacheServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.Set;

public class JpsCacheSimpleAction extends AnAction {
  private JpsCacheServerClient myCacheServerClient = new ArtifactoryJpsCacheServerClient();

  @Override
  public void actionPerformed(AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Set<String> cacheKeys = myCacheServerClient.getAllCacheKeys();
      System.out.println(cacheKeys);
      Set<String> binaryKeys = myCacheServerClient.getAllBinaryKeys();
      System.out.println(cacheKeys);
      List<String> hashes = GitRepositoryUtil.getLatestHashes(project, 20);
      System.out.println(hashes);
    });
  }
}
