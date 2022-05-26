// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.event.HyperlinkEvent;

public class DslErrorReporterImpl extends DslErrorReporter {
  private static final Logger LOG = Logger.getInstance(GroovyDslFileIndex.class);

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

    String errorMessage = e.getMessage();
    String content = new HtmlBuilder().append(
      HtmlChunk.p().addText(errorMessage == null ? e.toString() : errorMessage)
    ).append(
      HtmlChunk.p().child(
        HtmlChunk.link("", GroovyBundle.message("gdsl.investigate.link.label"))
      )
    ).toString();
    NOTIFICATION_GROUP.createNotification(GroovyBundle.message("gdsl.error.notification.title"), content, NotificationType.ERROR)
      .setListener(new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          InvestigateFix.analyzeStackTrace(project, exceptionText);
          notification.expire();
        }
      })
      .notify(project);
  }
}
