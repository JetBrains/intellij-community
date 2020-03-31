// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class DslErrorReporterImpl extends DslErrorReporter {
  private static final Logger LOG = Logger.getInstance(GroovyDslFileIndex.class);
  private final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Groovy DSL errors", NotificationDisplayType.BALLOON, true);

  public DslErrorReporterImpl() {
    NotificationsConfigurationImpl.remove("Groovy DSL parsing");
  }

  @Override
  public void invokeDslErrorPopup(Throwable e, final Project project, @NotNull VirtualFile vfile) {
    if (!GroovyDslFileIndex.isActivated(vfile)) {
      return;
    }

    final String exceptionText = ExceptionUtil.getThrowableText(e);
    LOG.info(exceptionText);
    GroovyDslFileIndex.disableFile(vfile, DslActivationStatus.Status.ERROR, exceptionText);


    if (!ApplicationManager.getApplication().isInternal() && !ProjectRootManager.getInstance(project).getFileIndex().isInContent(vfile)) {
      return;
    }

    String content = "<p>" + e.getMessage() + "</p><p><a href=\"\">Click here to investigate.</a></p>";
    NOTIFICATION_GROUP.createNotification("DSL script execution error", content, NotificationType.ERROR,
                                          new NotificationListener() {
                                            @Override
                                            public void hyperlinkUpdate(@NotNull Notification notification,
                                                                        @NotNull HyperlinkEvent event) {
                                              InvestigateFix.analyzeStackTrace(project, exceptionText);
                                              notification.expire();
                                            }
                                          }).notify(project);
  }
}
