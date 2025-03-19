// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndexUtils;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MavenRepositoriesHolder {
  private static final String UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID = "Unindexed maven repositories gradle detection";
  private static final Key<String> NOTIFICATION_KEY = Key.create(UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID);
  private final Project myProject;

  private volatile Set<MavenRemoteRepository> myRemoteRepositories;
  private volatile Set<String> myNotIndexedUrls;

  public MavenRepositoriesHolder(Project project) {
    myProject = project;
    myRemoteRepositories = Collections.emptySet();
    myNotIndexedUrls = Collections.emptySet();
  }

  public void updateNotIndexedUrls(List<String> repositories) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    if (repositories.isEmpty()) {
      myNotIndexedUrls = Collections.emptySet();
      ExternalSystemNotificationManager.getInstance(myProject).clearNotifications(
        UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID, NotificationSource.PROJECT_SYNC, GradleConstants.SYSTEM_ID);
    }
    else {
      myNotIndexedUrls = new HashSet<>(repositories);
    }
  }

  public static MavenRepositoriesHolder getInstance(@NotNull Project p) {
    return p.getService(MavenRepositoriesHolder.class);
  }

  public void update(Set<MavenRemoteRepository> remoteRepositories) {
    myRemoteRepositories = new HashSet<>(remoteRepositories);
  }

  public Set<MavenRemoteRepository> getRemoteRepositories() {
    return Collections.unmodifiableSet(myRemoteRepositories);
  }

  public boolean contains(String url) {
    final String pathOrUrl = MavenIndexUtils.normalizePathOrUrl(url);
    for (MavenRemoteRepository repository : myRemoteRepositories) {
      if (MavenIndexUtils.normalizePathOrUrl(repository.getUrl()).equals(pathOrUrl)) return true;
    }
    return false;
  }
}
