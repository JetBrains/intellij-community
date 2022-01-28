// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenIndexUtils;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenSearchIndex;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.idea.maven.indices.MavenIndexUpdateManager.IndexUpdatingState.IDLE;

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

  public void checkNotIndexedRepositories() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    ExternalSystemNotificationManager notificationManager = ExternalSystemNotificationManager.getInstance(myProject);
    if (ContainerUtil.isEmpty(myNotIndexedUrls)) {
      return;
    }

    if (notificationManager.isNotificationActive(NOTIFICATION_KEY)) return;

    final MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(myProject);
    for (MavenSearchIndex index : indicesManager.getIndex().getIndices()) {
      if (indicesManager.getUpdatingState(index) != IDLE) return;
    }

    @NlsSafe String lineBreak = "\n<br>";
    final NotificationData notificationData = new NotificationData(
      GradleBundle.message("gradle.integrations.maven.notification.not_updated_repository.title"),
      lineBreak + GradleBundle.message("gradle.integrations.maven.notification.not_updated_repository.text"),
      NotificationCategory.INFO,
      NotificationSource.PROJECT_SYNC);
    notificationData.setBalloonNotification(true);
    notificationData.setBalloonGroup(NotificationGroupManager.getInstance().getNotificationGroup(UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID));
    notificationData.setListener("#update", new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        List<MavenIndex> notIndexed =
          ContainerUtil.filter(indicesManager.getIndex().getIndices(), index -> isNotIndexed(index.getRepositoryPathOrUrl()));
        indicesManager.scheduleUpdateContent(notIndexed).thenRun(() -> {
          if (myNotIndexedUrls.isEmpty()) return;
          for (MavenSearchIndex index : notIndexed) {
            if (index.getUpdateTimestamp() != -1 || index.getFailureMessage() != null) {
              myNotIndexedUrls.remove(index.getRepositoryPathOrUrl());
            }
          }
        });
        notification.hideBalloon();
      }
    });

    notificationData.setListener("#disable", new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        final int result =
          Messages.showYesNoDialog(myProject,
                                   GradleBundle.message("gradle.integrations.maven.notification.to.be.disabled",
                                                        UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID),
                                   GradleBundle.message("gradle.integrations.maven.notification.detection"),
                                   GradleBundle.message("gradle.integrations.maven.notification.disable.text"),
                                   CommonBundle.getCancelButtonText(),
                                   Messages.getWarningIcon());
        if (result == Messages.YES) {
          NotificationsConfigurationImpl.getInstanceImpl().changeSettings(UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP_ID,
                                                                          NotificationDisplayType.NONE, false, false);

          notification.hideBalloon();
        }
      }
    });

    notificationManager.showNotification(GradleConstants.SYSTEM_ID, notificationData, NOTIFICATION_KEY);
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

  public boolean isNotIndexed(String url) {
    final String pathOrUrl = MavenIndexUtils.normalizePathOrUrl(url);
    for (String repository : myNotIndexedUrls) {
      if (MavenIndexUtils.normalizePathOrUrl(repository).equals(pathOrUrl)) return true;
    }
    return false;
  }
}
