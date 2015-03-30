/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class DslErrorReporterImpl extends DslErrorReporter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex");
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


    if (!ApplicationManagerEx.getApplicationEx().isInternal() && !ProjectRootManager.getInstance(project).getFileIndex().isInContent(vfile)) {
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
