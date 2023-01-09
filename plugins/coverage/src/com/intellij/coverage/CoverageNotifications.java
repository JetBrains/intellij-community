// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.util.concurrent.atomic.AtomicReference;

public class CoverageNotifications implements CoverageSuiteListener, Disposable {
  private final AtomicReference<Notification> myProjectOutOfDateNotification = new AtomicReference<>();


  public static CoverageNotifications getInstance(Project project) {
    return project.getService(CoverageNotifications.class);
  }

  public CoverageNotifications(Project project) {
    CoverageDataManager.getInstance(project).addSuiteListener(this, this);
  }

  @Override
  public void beforeSuiteChosen() {
    replaceAndExpireNotification(null);
  }

  public void addNotification(Notification newNotification) {
    replaceAndExpireNotification(newNotification);
  }

  private void replaceAndExpireNotification(Notification newNotification) {
    final Notification notification = myProjectOutOfDateNotification.getAndSet(newNotification);
    if (notification != null) {
      notification.expire();
      notification.hideBalloon();
    }
  }

  @Override
  public void afterSuiteChosen() {
  }

  @Override
  public void dispose() {
  }
}
