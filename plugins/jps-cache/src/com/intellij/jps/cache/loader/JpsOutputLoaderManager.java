package com.intellij.jps.cache.loader;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.jps.cache.client.ArtifactoryJpsServerClient;
import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.git.GitRepositoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JpsOutputLoaderManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.loader.JpsOutputLoaderManager");
  private static final String LATEST_COMMIT_ID = "JPS_CACHE.LATEST_COMMIT_ID";
  private static final int COMMITS_COUNT = 20;
  private List<JpsOutputLoader> myJpsOutputLoadersLoaders;
  private final JpsServerClient myServerClient;
  private final Project myProject;

  @NotNull
  public static JpsOutputLoaderManager getInstance(@NotNull Project project) {
    JpsOutputLoaderManager component = project.getComponent(JpsOutputLoaderManager.class);
    assert component != null;
    return component;
  }

  public JpsOutputLoaderManager(@NotNull Project project) {
    myProject = project;
    myServerClient = ArtifactoryJpsServerClient.INSTANCE;
  }

  public void load() {
    load(myServerClient.getAllCacheKeys());
  }

  public void load(@NotNull String currentCommitId) {
    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    if (previousCommitId != null && currentCommitId.equals(previousCommitId)) return;
    Set<String> allCacheKeys = myServerClient.getAllCacheKeys();
    if (allCacheKeys.contains(currentCommitId)) {
      getLoaders(myProject).forEach(loader -> ApplicationManager.getApplication().executeOnPooledThread(() -> loader.load(currentCommitId)));
      PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, currentCommitId);
    } else {
      load(allCacheKeys);
    }
  }

  private void load(Set<String> allCacheKeys) {
    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    GitRepositoryUtil.getLatestCommitHashes(myProject, COMMITS_COUNT).stream().filter(allCacheKeys::contains)
      .findFirst().ifPresent(commitId -> {
      if (previousCommitId != null && commitId.equals(previousCommitId)) return;
      LOG.debug("Loading JPS caches for commit: " + commitId);
      getLoaders(myProject).forEach(loader -> ApplicationManager.getApplication().executeOnPooledThread(() -> loader.load(commitId)));
      PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
    });
  }

  private List<JpsOutputLoader> getLoaders(@NotNull Project project) {
    if (myJpsOutputLoadersLoaders == null) {
      myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, project),
                                                new JpsCompilationOutputLoader(myServerClient, project));
      return myJpsOutputLoadersLoaders;
    }
    return myJpsOutputLoadersLoaders;
  }
}
